package ru.example.prodbackend.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.config.TestcontainersConfig;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.dto.ObservedItem;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class UnpackingSnapshotIntegrationTest {
    @Autowired private EntityManager entities;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UnpackingVerificationService service;
    @MockitoBean private LlmProxyClient client;

    @Test
    void externalCallHasNoTransactionAndConcurrentEditDoesNotChangeReport() {
        var tx = new TransactionTemplate(transactions);
        var fixture = tx.execute(status -> {
            var user = UserEntity.builder().email("unpacking-" + UUID.randomUUID()).password("hash").role(UserRole.USER).build();
            entities.persist(user);
            var project = MoveProjectEntity.builder().owner(user).name("Переезд").moveDate(Instant.now()).build();
            entities.persist(project);
            var room = RoomEntity.builder().project(project).name("Кухня").build();
            entities.persist(room);
            var box = BoxEntity.builder().project(project).room(room).number(1).status(BoxStatus.DELIVERED).build();
            entities.persist(box);
            var item = ItemEntity.builder().box(box).name("Зарядка").lost(true).build();
            entities.persist(item);
            return new Fixture(user.getId(), project.getId(), room.getId(), box.getId(), item.getId());
        });
        assertThat(fixture).isNotNull();
        try {
            when(client.verifyUnpacking(any(), eq("image/jpeg"), anyList())).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                List<InventoryItem> snapshot = call.getArgument(2);
                assertThat(snapshot).containsExactly(new InventoryItem(fixture.item(), "Зарядка"));
                tx.executeWithoutResult(status -> entities.find(ItemEntity.class, fixture.item()).setName("Изменено во время сверки"));
                return List.of(new ObservedItem(fixture.item(), "Зарядное устройство"));
            });
            var photo = new MockMultipartFile("photo", new byte[]{(byte)255, (byte)216, (byte)255});
            var result = service.verify(fixture.user(), fixture.project(), fixture.box(), photo);
            assertThat(result.matched()).singleElement().satisfies(item -> assertThat(item.name()).isEqualTo("Зарядка"));
            tx.executeWithoutResult(status -> {
                assertThat(entities.find(ItemEntity.class, fixture.item()).getName()).isEqualTo("Изменено во время сверки");
                assertThat(entities.find(ItemEntity.class, fixture.item()).isLost()).isTrue();
                assertThat(entities.find(BoxEntity.class, fixture.box()).getStatus()).isEqualTo(BoxStatus.DELIVERED);
            });
        } finally {
            tx.executeWithoutResult(status -> {
                entities.remove(entities.find(ItemEntity.class, fixture.item()));
                entities.remove(entities.find(BoxEntity.class, fixture.box()));
                entities.remove(entities.find(RoomEntity.class, fixture.room()));
                entities.remove(entities.find(MoveProjectEntity.class, fixture.project()));
                entities.remove(entities.find(UserEntity.class, fixture.user()));
            });
        }
    }

    private record Fixture(UUID user, UUID project, UUID room, UUID box, UUID item) { }
}
