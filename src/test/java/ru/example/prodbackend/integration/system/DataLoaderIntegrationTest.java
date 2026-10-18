package ru.example.prodbackend.integration.system;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.auth.service.AuthenticationService;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.config.DataLoader;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.support.BaseIntegrationTest;
import ru.example.prodbackend.task.dto.request.CreateMoveTaskRequest;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.taskset.service.TaskSetService;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class DataLoaderIntegrationTest extends BaseIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private MoveProjectService moveProjectService;
    @Autowired private RoomService roomService;
    @Autowired private BoxService boxService;
    @Autowired private MoveTaskService moveTaskService;
    @Autowired private ItemService itemService;
    @Autowired private TaskSetService taskSetService;

    @Test
    void seedsSmallCorporateMovesAndAllowsAllTenAccountsToSignIn() {
        loader().run(new DefaultApplicationArguments());
        entityManager.flush();
        entityManager.clear();

        assertThat(count("UserEntity")).isEqualTo(11); // Ten demo accounts plus the service administrator.
        assertThat(count("RefreshTokenEntity")).isZero(); // Sessions are created on sign-in, not on startup.
        assertThat(count("MoveProjectEntity")).isEqualTo(22);
        assertThat(count("ProjectParticipantEntity")).isEqualTo(8);
        assertThat(count("TaskFolderEntity")).isEqualTo(44);
        assertThat(count("TaskSetEntity")).isEqualTo(10);
        assertThat(count("TaskSetApplicationEntity")).isEqualTo(22);
        assertThat(count("MoveTaskEntity")).isEqualTo(88);
        assertThat(count("RoomEntity")).isEqualTo(44);
        assertThat(count("BoxEntity")).isEqualTo(72);
        assertThat(count("ItemEntity")).isEqualTo(100);
        assertThat(count("FirstDayChecklistItemEntity")).isZero();

        for (int number = 1; number <= 10; number++) {
            String login = "u" + number;
            var user = user(login);
            assertThat(user.getFirstName()).isEqualTo(login);
            assertThat(user.getLastName()).isEqualTo("Сотрудник");
            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(passwordEncoder.matches("1", user.getPassword())).isTrue();
            assertThat(token(login, "1")).isNotBlank();
            var projects = moveProjectRepository.findAllByArchiveFilter(user.getId(), false, PageRequest.of(0, 100));
            assertThat(projects.getContent()).hasSize(3);
            for (var project : projects) {
                assertThat(project.getArchivedAt()).isNull();
                var tasks = entityManager.createQuery("from MoveTaskEntity t where t.project.id = :project", MoveTaskEntity.class)
                        .setParameter("project", project.getId()).getResultList();
                assertThat(tasks).hasSize(4).allSatisfy(task -> {
                    assertThat(task.getFolder()).isNotNull();
                    assertThat(task.getFolder().getProject().getId()).isEqualTo(project.getId());
                    assertThat(task.getAssignee()).isNotNull();
                    assertThat(moveProjectRepository.hasAccess(project.getId(), task.getAssignee().getId())).isTrue();
                    assertThat(task.getDueType()).isEqualTo(TaskDueType.RELATIVE);
                    assertThat(task.getDueDate()).isEqualTo(project.getMoveDate().atOffset(java.time.ZoneOffset.UTC)
                            .toLocalDate().plusDays(task.getOffsetDays()));
                });
                assertThat(tasks).anyMatch(task -> task.getCompletedAt() != null)
                        .anyMatch(task -> task.getCompletedAt() == null).anyMatch(task -> task.getLinkedItem() != null);
            }
        }

        assertThat(entityManager.createQuery("from ItemEntity", ItemEntity.class).getResultList()).allSatisfy(item -> {
            assertThat(item.getQuantity()).isPositive();
            if (item.getBox().getStatus() != BoxStatus.PACKING) assertThat(item.getPackedAt()).isNotNull();
            if (item.getBox().getStatus() == BoxStatus.UNPACKED) assertThat(item.getUnpackedAt()).isNotNull();
        }).anyMatch(ItemEntity::isLost).anyMatch(ItemEntity::isFirstDay).anyMatch(ItemEntity::isFragile);
        for (String shared : List.of("Общий переезд офиса из Феррума", "Общий переезд офиса на Неве")) {
            var project = project(shared);
            assertThat(entityManager.createQuery("select b.status from BoxEntity b where b.project.id = :project", BoxStatus.class)
                    .setParameter("project", project.getId()).getResultList()).contains(BoxStatus.values());
        }
        assertThat(passwordEncoder.matches("test-admin-password", user("admin").getPassword())).isTrue();
        assertThat(user("admin").getLastName()).isEqualTo("Администратор");
    }

    @Test
    void judgesCanAddInventoryAndTasksToSharedProjectsButOtherGroupCannotReadThem() throws Exception {
        loader().run(new DefaultApplicationArguments());
        var project = project("Общий переезд офиса из Феррума");
        UUID member = user("u2").getId();
        UUID roomId = entityManager.createQuery("select r.id from RoomEntity r where r.project.id = :project order by r.id", UUID.class)
                .setParameter("project", project.getId()).setMaxResults(1).getSingleResult();
        var box = boxService.create(member, project.getId(), CreateBoxRequest.builder()
                .number(7).title("Коробка жюри").roomId(roomId).build());
        var item = itemService.create(member, project.getId(), box.getId(), CreateItemRequest.builder().name("Тестовый переходник").build());
        var task = moveTaskService.create(project.getId(), CreateMoveTaskRequest.builder().title("Проверить новую коробку")
                .stage(TaskStage.BEFORE_MOVE).assigneeId(member).linkedItemId(item.getId()).build(), member);
        assertThat(task.getLinkedItemId()).isEqualTo(item.getId());
        assertThat(itemService.findById(user("u1").getId(), project.getId(), box.getId(), item.getId()).getName())
                .isEqualTo("Тестовый переходник");

        mockMvc.perform(get("/api/move-projects/{id}", project.getId())
                        .header("Authorization", "Bearer " + token("u2", "1"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/move-projects/{id}", project.getId())
                        .header("Authorization", "Bearer " + token("u6", "1"))).andExpect(status().isNotFound());
        var privateProject = project("Переезд IT-отдела — u1");
        mockMvc.perform(get("/api/move-projects/{id}", privateProject.getId())
                        .header("Authorization", "Bearer " + token("u2", "1"))).andExpect(status().isNotFound());
    }

    @Test
    void restartPreservesPasswordsRenamesDeletionsInventoryAndSessions() {
        var loader = loader();
        loader.run(new DefaultApplicationArguments());
        var owner = user("u1");
        token("u1", "1");
        String updatedPassword = passwordEncoder.encode("changed-password");
        owner.setPassword(updatedPassword);
        owner.setFirstName("Имя проверяющего");
        owner.setLastName("Фамилия проверяющего");
        var project = project("Переезд IT-отдела — u1");
        var emptyBox = entityManager.createQuery("from BoxEntity b where b.project.id = :project and b.number = 3", BoxEntity.class)
                .setParameter("project", project.getId()).getSingleResult();
        boxService.delete(owner.getId(), project.getId(), emptyBox.getId());
        project.setName("Переезд переименован жюри");
        var room = entityManager.createQuery("from RoomEntity r where r.project.id = :project order by r.id", RoomEntity.class)
                .setParameter("project", project.getId()).setMaxResults(1).getSingleResult();
        room.setIconKey("heart");
        var item = entityManager.createQuery("from ItemEntity i where i.box.project.id = :project order by i.id", ItemEntity.class)
                .setParameter("project", project.getId()).setMaxResults(1).getSingleResult();
        item.setName("Название изменено жюри");
        entityManager.flush();

        var before = new HashMap<String, List<UUID>>();
        for (String entity : List.of("UserEntity", "RefreshTokenEntity", "MoveProjectEntity", "ProjectParticipantEntity",
                "TaskFolderEntity", "TaskSetEntity", "TaskSetItemEntity", "TaskSetApplicationEntity", "RoomEntity",
                "BoxEntity", "ItemEntity", "MoveTaskEntity", "ProjectEventEntity")) {
            before.put(entity, entityManager.createQuery("select e.id from " + entity + " e order by e.id", UUID.class).getResultList());
        }
        loader.run(new DefaultApplicationArguments());
        entityManager.flush();
        entityManager.clear();
        before.forEach((entity, ids) -> assertThat(entityManager.createQuery(
                "select e.id from " + entity + " e order by e.id", UUID.class).getResultList()).isEqualTo(ids));
        assertThat(user("u1").getPassword()).isEqualTo(updatedPassword);
        assertThat(user("u1").getFirstName()).isEqualTo("Имя проверяющего");
        assertThat(user("u1").getLastName()).isEqualTo("Фамилия проверяющего");
        assertThat(entityManager.find(MoveProjectEntity.class, project.getId()).getName()).isEqualTo("Переезд переименован жюри");
        assertThat(entityManager.find(RoomEntity.class, room.getId()).getIconKey()).isEqualTo("heart");
        assertThat(entityManager.find(ItemEntity.class, item.getId()).getName()).isEqualTo("Название изменено жюри");
    }

    @Test
    void existingAccountIsNotResetOrPopulatedWithUnrequestedData() {
        userRepository.saveAndFlush(UserEntity.builder().email("u1").firstName("Существующий пользователь")
                .password(passwordEncoder.encode("original-password")).role(UserRole.USER).build());
        userRepository.saveAndFlush(UserEntity.builder().email("u2").firstName("u2").lastName("  ")
                .password(passwordEncoder.encode("original-password")).role(UserRole.USER).build());
        loader().run(new DefaultApplicationArguments());
        assertThat(user("u1").getFirstName()).isEqualTo("Существующий пользователь");
        assertThat(user("u1").getLastName()).isEqualTo("Сотрудник");
        assertThat(user("u2").getLastName()).isEqualTo("Сотрудник");
        assertThat(passwordEncoder.matches("original-password", user("u1").getPassword())).isTrue();
        assertThat(moveProjectRepository.findAllByArchiveFilter(user("u1").getId(), null, PageRequest.of(0, 100))).isEmpty();
        assertThat(count("UserEntity")).isEqualTo(11);
        assertThat(token("u10", "1")).isNotBlank();
    }

    private DataLoader loader() {
        var loader = new DataLoader(itemService, userRepository, passwordEncoder, moveProjectService,
                roomService, boxService, moveTaskService, taskSetService);
        ReflectionTestUtils.setField(loader, "adminPassword", "test-admin-password");
        return loader;
    }

    private UserEntity user(String login) { return userRepository.findByEmail(login).orElseThrow(); }

    private String token(String login, String password) {
        return authenticationService.authenticate(new AuthenticationRequest(login, password)).getAccessToken();
    }

    private MoveProjectEntity project(String name) {
        return entityManager.createQuery("from MoveProjectEntity p where p.name = :name", MoveProjectEntity.class)
                .setParameter("name", name).getSingleResult();
    }

    private long count(String entity) {
        return entityManager.createQuery("select count(e) from " + entity + " e", Long.class).getSingleResult();
    }
}
