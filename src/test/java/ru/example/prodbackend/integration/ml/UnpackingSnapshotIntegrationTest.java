package ru.example.prodbackend.integration.ml;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Value;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.dto.ObservedItem;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.support.BaseMlIntegrationTest;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UnpackingSnapshotIntegrationTest extends BaseMlIntegrationTest {
    @Autowired private EntityManager entities;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private UnpackingVerificationService service;

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
            when(llmClient.verifyUnpacking(any(), eq("image/jpeg"), anyList())).thenAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                List<InventoryItem> snapshot = call.getArgument(2);
                assertThat(snapshot).containsExactly(new InventoryItem(fixture.getItem(), "Зарядка"));
                tx.executeWithoutResult(status -> entities.find(ItemEntity.class, fixture.getItem()).setName("Изменено во время сверки"));
                return List.of(new ObservedItem(fixture.getItem(), "Зарядное устройство"));
            });
            var photo = new MockMultipartFile("photo", new byte[]{(byte)255, (byte)216, (byte)255});
            var result = service.verify(fixture.getUser(), fixture.getProject(), fixture.getBox(), photo);
            assertThat(result.getMatched()).singleElement().satisfies(item -> assertThat(item.getName()).isEqualTo("Зарядка"));
            tx.executeWithoutResult(status -> {
                assertThat(entities.find(ItemEntity.class, fixture.getItem()).getName()).isEqualTo("Изменено во время сверки");
                assertThat(entities.find(ItemEntity.class, fixture.getItem()).isLost()).isTrue();
                assertThat(entities.find(BoxEntity.class, fixture.getBox()).getStatus()).isEqualTo(BoxStatus.DELIVERED);
            });
        } finally {
            tx.executeWithoutResult(status -> {
                entities.remove(entities.find(ItemEntity.class, fixture.getItem()));
                entities.remove(entities.find(BoxEntity.class, fixture.getBox()));
                entities.remove(entities.find(RoomEntity.class, fixture.getRoom()));
                entities.remove(entities.find(MoveProjectEntity.class, fixture.getProject()));
                entities.remove(entities.find(UserEntity.class, fixture.getUser()));
            });
        }
    }

    @Value
    private static class Fixture {

        private final UUID user;

        private final UUID project;

        private final UUID room;

        private final UUID box;

        private final UUID item;
    }
}
