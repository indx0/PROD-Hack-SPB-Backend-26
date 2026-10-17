package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.DataLoader;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.user.repository.UserRepository;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.checklist.service.FirstDayChecklistService;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.task.service.MoveTaskService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class DataLoaderIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private MoveProjectService moveProjectService;
    @Autowired
    private RoomService roomService;
    @Autowired
    private BoxService boxService;
    @Autowired
    private MoveTaskService moveTaskService;
    @Autowired
    private FirstDayChecklistService checklistService;
    @Autowired
    private ItemService itemService;

    @Test
    void loadsDemoMoveForUser1() {
        // Run inside the test transaction so demo data is rolled back, including in the shared CI database.
        DataLoader loader = loader();
        LocalDate beforeLoad = LocalDate.now(ZoneOffset.UTC);
        loader.run(new DefaultApplicationArguments(new String[0]));
        LocalDate afterLoad = LocalDate.now(ZoneOffset.UTC);
        entityManager.flush();
        entityManager.clear();

        MoveProjectEntity project = entityManager.createQuery("from MoveProjectEntity", MoveProjectEntity.class)
                .getSingleResult();
        assertThat(project.getOwner().getEmail()).isEqualTo("user1");
        LocalDate moveDate = project.getMoveDate().atOffset(ZoneOffset.UTC).toLocalDate();
        assertThat(moveDate).isBetween(beforeLoad.plusDays(14), afterLoad.plusDays(14));
        assertThat(project.getMoveDate().atOffset(ZoneOffset.UTC).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(count("UserEntity")).isEqualTo(2);
        assertThat(entityManager.createQuery(
                "select count(t) from RefreshTokenEntity t group by t.user.id", Long.class).getResultList())
                .containsExactlyInAnyOrder(1L, 1L);
        assertThat(count("RoomEntity")).isEqualTo(4);
        assertThat(count("ItemEntity")).isEqualTo(12);
        assertThat(entityManager.createQuery("from ItemEntity", ItemEntity.class).getResultList()).allSatisfy(item -> {
            if (item.getBox().getStatus() != BoxStatus.PACKING) assertThat(item.getPackedAt()).isNotNull();
            if (item.getBox().getStatus() == BoxStatus.UNPACKED) assertThat(item.getUnpackedAt()).isNotNull();
        });
        assertThat(entityManager.createQuery(
                "select i.quantity from ItemEntity i where i.name = 'Тарелки'", Integer.class)
                .getSingleResult()).isEqualTo(6);
        assertThat(entityManager.createQuery(
                "select i.firstDay from ItemEntity i where i.name = 'Постельное бельё'", Boolean.class)
                .getSingleResult()).isTrue();
        assertThat(count("MoveTaskEntity")).isEqualTo(12);
        assertThat(count("FirstDayChecklistItemEntity")).isEqualTo(6);
        var linked = entityManager.createQuery("select c from FirstDayChecklistItemEntity c where c.item is not null", FirstDayChecklistItemEntity.class).getResultList();
        assertThat(linked).singleElement().satisfies(point -> {
            assertThat(point.getName()).isEqualTo("Постельное бельё");
            assertThat(point.getItem().getName()).isEqualTo("Постельное бельё");
            assertThat(point.getItem().isFirstDay()).isTrue();
        });
        assertThat(entityManager.createQuery("select count(c) from FirstDayChecklistItemEntity c where c.item is null", Long.class).getSingleResult()).isEqualTo(5);
        assertThat(count("ProjectEventEntity")).isEqualTo(18);
        assertThat(entityManager.createQuery("from BoxEntity", BoxEntity.class).getResultList())
                .hasSize(5)
                .allSatisfy(box -> {
                    assertThat(box.getProject().getId()).isEqualTo(project.getId());
                    assertThat(box.getRoom().getProject().getId()).isEqualTo(project.getId());
                    assertThat(box.isFragile()).isEqualTo(box.getNumber() == 2);
                    assertThat(box.isFirstDay()).isEqualTo(box.getNumber() == 3);
                    assertThat(box.isCarryWithMe()).isEqualTo(box.getNumber() == 5);
                    String title = switch (box.getNumber()) {
                        case 2 -> "Посуда для кухни";
                        case 3 -> "Коробка первого дня";
                        case 5 -> "Везу с собой";
                        default -> "";
                    };
                    assertThat(box.getTitle()).isEqualTo(title);
                })
                .extracting(BoxEntity::getStatus).containsExactlyInAnyOrder(BoxStatus.values());
        assertThat(entityManager.createQuery("from BoxEntity", BoxEntity.class).getResultList())
                .extracting(BoxEntity::getNumber).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertTaskDueDate("Заказать грузчиков", moveDate.minusDays(14));
        assertTaskDueDate("Передать ключи", moveDate);
        assertTaskDueDate("Подключить интернет", moveDate.plusDays(1));
        assertTaskDueDate("Упаковать кухню", moveDate.minusDays(2));
        assertThat(entityManager.createQuery("select count(t) from MoveTaskEntity t where t.completedAt is not null", Long.class).getSingleResult()).isEqualTo(2);
        assertThat(entityManager.createQuery("select count(i) from FirstDayChecklistItemEntity i where i.completedAt is not null", Long.class).getSingleResult()).isEqualTo(2);
        assertThat(entityManager.createQuery("select count(e) from ProjectEventEntity e where e.eventType = ru.example.prodbackend.event.entity.ProjectEventType.PROJECT_CREATED", Long.class).getSingleResult()).isEqualTo(1);
        assertThat(passwordEncoder.matches("test-user-password", userRepository.findByEmail("user1").orElseThrow().getPassword())).isTrue();
    }

    @Test
    void repeatedRunPreservesUsersDemoObjectsSessionsAndEvents() {
        DataLoader loader = loader();
        var args = new DefaultApplicationArguments(new String[0]);
        loader.run(args);
        entityManager.flush();
        var before = new HashMap<String, List<UUID>>();
        for (String entity : new String[]{"UserEntity", "RefreshTokenEntity", "MoveProjectEntity", "RoomEntity", "BoxEntity", "ItemEntity", "MoveTaskEntity", "FirstDayChecklistItemEntity", "ProjectEventEntity"}) {
            before.put(entity, entityManager.createQuery("select e.id from " + entity + " e order by e.id", UUID.class).getResultList());
        }
        String password = userRepository.findByEmail("user1").orElseThrow().getPassword();
        var existingBox = entityManager.createQuery("from BoxEntity b where b.number = 2", BoxEntity.class).getSingleResult();
        existingBox.setTitle("Моя подпись");
        existingBox.setFragile(false);
        existingBox.setFirstDay(true);
        existingBox.setCarryWithMe(true);
        UUID existingBoxId = existingBox.getId();
        ReflectionTestUtils.setField(loader, "user1Password", "new-password-must-not-overwrite-user");
        loader.run(args);
        entityManager.flush();
        entityManager.clear();
        before.forEach((entity, ids) -> assertThat(entityManager.createQuery("select e.id from " + entity + " e order by e.id", UUID.class).getResultList()).isEqualTo(ids));
        assertThat(userRepository.findByEmail("user1").orElseThrow().getPassword()).isEqualTo(password);
        var preserved = entityManager.find(BoxEntity.class, existingBoxId);
        assertThat(preserved.getTitle()).isEqualTo("Моя подпись");
        assertThat(preserved.isFragile()).isFalse();
        assertThat(preserved.isFirstDay()).isTrue();
        assertThat(preserved.isCarryWithMe()).isTrue();
    }

    private DataLoader loader() {
        var loader = new DataLoader(itemService, userRepository, passwordEncoder, refreshTokenService, moveProjectRepository,
                moveProjectService, roomService, boxService, moveTaskService, checklistService);
        ReflectionTestUtils.setField(loader, "adminPassword", "test-admin-password");
        ReflectionTestUtils.setField(loader, "user1Password", "test-user-password");
        return loader;
    }

    private void assertTaskDueDate(String title, LocalDate expected) {
        MoveTaskEntity task = entityManager.createQuery(
                        "from MoveTaskEntity t where t.title = :title", MoveTaskEntity.class)
                .setParameter("title", title).getSingleResult();
        assertThat(task.getDueDate()).isEqualTo(expected);
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }
}
