package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.repository.BoxRepository;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.config.JdbcCaptureConfig;
import ru.example.prodbackend.config.JdbcCaptureConfig.Execution;
import ru.example.prodbackend.config.JdbcCaptureConfig.JdbcCapture;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.dto.ItemResponse;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, JdbcCaptureConfig.class})
@Transactional
class ItemBoxQueryIntegrationTest {
    @Autowired private EntityManager entityManager;
    @Autowired private BoxService boxService;
    @Autowired private ItemService itemService;
    @Autowired private BoxRepository boxRepository;
    @Autowired private JdbcCapture jdbcCapture;

    private UserEntity owner;
    private MoveProjectEntity project;
    private RoomEntity kitchen;
    private RoomEntity bedroom;

    @BeforeEach
    void setUp() {
        owner = UserEntity.builder().email("query-test-" + UUID.randomUUID())
                .password("test-password-hash").role(UserRole.USER).build();
        entityManager.persist(owner);
        project = MoveProjectEntity.builder().owner(owner).name("Переезд")
                .moveDate(Instant.parse("2026-10-30T00:00:00Z")).build();
        entityManager.persist(project);
        kitchen = room("Кухня");
        bedroom = room("Спальня");
    }

    @AfterEach
    void stopRecording() {
        jdbcCapture.stop();
    }

    @Test
    void deletesLargeContentsWithOneStatementWithoutLoadingItems() {
        BoxEntity target = box(kitchen, 1, BoxStatus.PACKING, false);
        for (int i = 0; i < 1000; i++) {
            item(target, "Вещь " + i);
        }
        BoxEntity other = box(bedroom, 2, BoxStatus.PACKING, false);
        ItemEntity preserved = item(other, "Сохранить");
        flushAndClear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        jdbcCapture.start();

        boxService.delete(owner.getId(), project.getId(), target.getId());

        var executions = jdbcCapture.stop();
        assertThat(executions.stream().filter(e -> e.sql().startsWith("delete from items"))).hasSize(1);
        assertThat(executions).noneMatch(e -> e.sql().startsWith("select") && e.sql().contains("from items"));
        assertThat(statistics.getEntityStatistics(ItemEntity.class.getName()).getLoadCount()).isZero();
        entityManager.clear();
        assertThat(entityManager.find(BoxEntity.class, target.getId())).isNull();
        assertThat(entityManager.find(ItemEntity.class, preserved.getId())).isNotNull();
        assertThat(entityManager.createQuery("select count(i) from ItemEntity i where i.box.id = :id", Long.class)
                .setParameter("id", target.getId()).getSingleResult()).isZero();
    }

    @Test
    void suggestionsLimitEntityLoadingAndKeepRankingWithoutDuplicates() {
        BoxEntity sourceBox = box(kitchen, 300, BoxStatus.PACKING, false);
        ItemEntity source = item(sourceBox, "Кабель");
        BoxEntity matching = box(bedroom, 1, BoxStatus.LOADED, false);
        item(matching, "Кабель USB");
        item(matching, "Кабель HDMI");
        for (int i = 10; i < 160; i++) {
            box(kitchen, i, BoxStatus.PACKING, false);
        }
        item(box(kitchen, 2, BoxStatus.UNPACKED, false), "Кабель");
        flushAndClear();
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        jdbcCapture.start();

        var suggestions = itemService.suggestions(owner.getId(), project.getId(), source.getId(), 2);

        var executions = jdbcCapture.stop();
        assertThat(suggestions).extracting(s -> s.getBoxId()).containsExactly(sourceBox.getId(), matching.getId());
        assertThat(suggestions).allMatch(s -> s.isContentsMatch());
        assertThat(suggestions.get(0).isSameRoom()).isTrue();
        assertThat(suggestions.get(1).isSameRoom()).isFalse();
        assertThat(statistics.getEntityStatistics(BoxEntity.class.getName()).getLoadCount()).isLessThanOrEqualTo(3);
        assertThat(executions).hasSize(3);
        assertThat(executions.get(2).sql()).contains("fetch first");
    }

    @Test
    void suggestionsSupportEmptyMatchesAndKeepOwnerAndStatusFilters() {
        BoxEntity sameRoom = box(kitchen, 1, BoxStatus.PACKING, false);
        box(kitchen, 2, BoxStatus.UNPACKED, false);
        box(bedroom, 3, BoxStatus.PACKING, false);
        flushAndClear();

        assertThat(boxRepository.findSuggestions(project.getId(), owner.getId(), kitchen.getId(),
                List.of(), PageRequest.of(0, 10))).extracting(BoxEntity::getId).containsExactly(sameRoom.getId());
        assertThat(boxRepository.findSuggestions(project.getId(), UUID.randomUUID(), kitchen.getId(),
                List.of(sameRoom.getId()), PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void createsOneHundredItemsUsingJdbcBatchesInInputOrder() {
        BoxEntity box = box(kitchen, 1, BoxStatus.PACKING, false);
        flushAndClear();
        List<String> names = IntStream.range(0, 100).mapToObj(i -> " Вещь " + i + " ").toList();
        jdbcCapture.start();

        var created = itemService.createBatch(owner.getId(), project.getId(), box.getId(),
                CreateItemsRequest.builder().names(names).build());

        var inserts = jdbcCapture.stop().stream().filter(e -> e.sql().startsWith("insert into items")).toList();
        assertThat(inserts).extracting(Execution::batchSize).containsExactly(50, 50);
        assertThat(created).extracting(ItemResponse::getName)
                .containsExactlyElementsOf(names.stream().map(String::strip).toList());
        assertThat(created).extracting(ItemResponse::getId).doesNotHaveDuplicates();
        entityManager.clear();
        assertThat(entityManager.createQuery("select count(i) from ItemEntity i where i.box.id = :id", Long.class)
                .setParameter("id", box.getId()).getSingleResult()).isEqualTo(100);
    }

    @Test
    void compositeIndexAndPagesUseCreatedAtThenIdEvenWhenTimestampsMatch() {
        BoxEntity box = box(kitchen, 1, BoxStatus.PACKING, false);
        for (int i = 0; i < 5; i++) {
            item(box, "Вещь " + i);
        }
        entityManager.flush();
        entityManager.createNativeQuery("update items set created_at = :time where box_id = :boxId")
                .setParameter("time", Instant.parse("2026-10-16T12:00:00Z"))
                .setParameter("boxId", box.getId()).executeUpdate();
        entityManager.clear();
        List<?> expected = entityManager.createNativeQuery(
                "select id from items where box_id = :boxId order by created_at, id", UUID.class)
                .setParameter("boxId", box.getId()).getResultList();

        for (int page = 0; page < 5; page++) {
            PaginationRequest pagination = new PaginationRequest();
            pagination.setPage(page);
            pagination.setSize(1);
            var result = itemService.findAll(owner.getId(), project.getId(), box.getId(), pagination);
            assertThat(result.getContent()).extracting(ItemResponse::getId).containsExactly((UUID) expected.get(page));
            assertThat(result.getTotalElements()).isEqualTo(5);
        }
        String index = (String) entityManager.createNativeQuery("""
                select indexdef from pg_indexes
                where schemaname = current_schema() and tablename = 'items'
                  and indexname = 'idx_items_box_created_id'
                """).getSingleResult();
        assertThat(index).contains("(box_id, created_at, id)");
    }

    @Test
    void summaryPreservesRoomOrderCountsAndRoundedProgress() {
        box(kitchen, 9, BoxStatus.PACKING, true);
        box(kitchen, 10, BoxStatus.UNPACKED, false);
        box(kitchen, 11, BoxStatus.PACKED, true);
        box(bedroom, 1, BoxStatus.LOADED, true);
        box(bedroom, 2, BoxStatus.DELIVERED, false);
        room("Без коробок");
        flushAndClear();

        var summary = boxService.summary(owner.getId(), project.getId());

        assertThat(summary.getTotalBoxes()).isEqualTo(5);
        assertThat(summary.getDeferredContentsCount()).isEqualTo(3);
        assertThat(summary.getCountsByStatus().values()).containsOnly(1L);
        assertThat(summary.getRooms()).extracting(r -> r.getRoomId()).containsExactly(bedroom.getId(), kitchen.getId());
        assertThat(summary.getRooms().get(0).getUnpackingProgressPercent()).isZero();
        assertThat(summary.getRooms().get(1).getUnpackingProgressPercent()).isEqualTo(33);
        assertThat(summary.getRooms().get(1).getCountsByStatus().get(BoxStatus.LOADED)).isZero();
    }

    @Test
    void summaryOfEmptyProjectReturnsZeroCountsAndNoRooms() {
        flushAndClear();

        var summary = boxService.summary(owner.getId(), project.getId());

        assertThat(summary.getTotalBoxes()).isZero();
        assertThat(summary.getDeferredContentsCount()).isZero();
        assertThat(summary.getCountsByStatus()).hasSize(BoxStatus.values().length);
        assertThat(summary.getCountsByStatus().values()).containsOnly(0L);
        assertThat(summary.getRooms()).isEmpty();
    }

    private RoomEntity room(String name) {
        var room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private BoxEntity box(RoomEntity room, int number, BoxStatus status, boolean deferred) {
        var box = BoxEntity.builder().project(project).room(room).number(number)
                .status(status).contentsDeferred(deferred).build();
        entityManager.persist(box);
        return box;
    }

    private ItemEntity item(BoxEntity box, String name) {
        var item = ItemEntity.builder().box(box).name(name).build();
        entityManager.persist(item);
        return item;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
