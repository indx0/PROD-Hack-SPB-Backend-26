package ru.example.prodbackend.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;
import ru.example.prodbackend.event.entity.EventObjectType;
import ru.example.prodbackend.event.entity.ProjectEventEntity;
import ru.example.prodbackend.event.entity.ProjectEventType;
import ru.example.prodbackend.item.entity.ItemEntity;
import ru.example.prodbackend.move.entity.MoveProjectEntity;
import ru.example.prodbackend.room.entity.RoomEntity;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import ru.example.prodbackend.user.repository.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final EntityManager entityManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${USER1_PASSWORD}")
    private String user1Password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createUser("admin", adminPassword, "Алексей", "Админ", UserRole.ADMIN);
        UserEntity user = createUser("user1", user1Password, "Анна", "Пользователь", UserRole.USER);
        createDemoProject(user);

        log.info(
                "Demo data loaded: admin and user1, one refresh session per user, full demo move for user1"
        );
    }

    private UserEntity createUser(
            String email,
            String password,
            String firstName,
            String lastName,
            UserRole role
    ) {
        UserEntity user = UserEntity.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .build();

        userRepository.saveAndFlush(user);

        refreshTokenService.createRefreshToken(user);
        return user;
    }

    private void createDemoProject(UserEntity user) {
        MoveProjectEntity project = MoveProjectEntity.builder()
                .owner(user)
                .name("Переезд в новую квартиру")
                .moveDate(LocalDate.now().plusDays(14))
                .originAddress("Москва, ул. Лесная, д. 10, кв. 15")
                .destinationAddress("Москва, ул. Садовая, д. 25, кв. 42")
                .build();
        entityManager.persist(project);
        createEvent(project, ProjectEventType.PROJECT_CREATED, EventObjectType.PROJECT,
                project.getId(), "Создан проект переезда");

        RoomEntity kitchen = createRoom(project, "Кухня");
        RoomEntity livingRoom = createRoom(project, "Гостиная");
        RoomEntity bedroom = createRoom(project, "Спальня");
        RoomEntity bathroom = createRoom(project, "Ванная");

        createBox(project, livingRoom, BoxStatus.PACKING, true);
        createBox(project, kitchen, BoxStatus.PACKED, false, "Тарелки", "Кружки", "Столовые приборы");
        createBox(project, bedroom, BoxStatus.LOADED, false, "Постельное бельё", "Подушки", "Плед");
        createBox(project, livingRoom, BoxStatus.DELIVERED, false, "Книги", "Настольная лампа", "Фоторамки");
        BoxEntity bathroomBox = createBox(project, bathroom, BoxStatus.UNPACKED, false,
                "Полотенца", "Шампунь");
        entityManager.persist(ItemEntity.builder().box(bathroomBox).name("Фен").lost(true).build());

        createTask(project, "Выбрать перевозчика", "Сравнить условия и забронировать машину.",
                TaskStage.BEFORE_MOVE, -10, "Анна", true);
        createTask(project, "Купить упаковку", "Подготовить коробки, скотч и защитную плёнку.",
                TaskStage.BEFORE_MOVE, -7, "Анна", true);
        createTask(project, "Упаковать кухню", "Обернуть хрупкую посуду и подписать коробки.",
                TaskStage.BEFORE_MOVE, -2, "Алексей", false);
        createTask(project, "Встретить грузчиков", "Показать коробки и порядок погрузки.",
                TaskStage.MOVING_DAY, 0, "Анна", false);
        createTask(project, "Проверить старую квартиру", "Закрыть окна и проверить все комнаты.",
                TaskStage.MOVING_DAY, 0, "Алексей", false);
        createTask(project, "Принять коробки", "Сверить доставленные коробки со списком.",
                TaskStage.MOVING_DAY, 0, "Анна", false);
        createTask(project, "Распаковать вещи", "Разложить вещи по комнатам и проверить потери.",
                TaskStage.AFTER_MOVE, 1, "Анна", false);
        createTask(project, "Подключить интернет", "Согласовать подключение с провайдером.",
                TaskStage.AFTER_MOVE, 2, "Алексей", false);
        createTask(project, "Сдать упаковку", "Собрать картон и отвезти на переработку.",
                TaskStage.AFTER_MOVE, 7, "Анна", false);

        String[] checklist = {"Питьевая вода", "Зарядки для телефонов", "Документы", "Аптечка",
                "Средства гигиены", "Одежда на первый день"};
        for (int i = 0; i < checklist.length; i++) {
            entityManager.persist(FirstDayChecklistItemEntity.builder()
                    .project(project).name(checklist[i])
                    .completedAt(i < 2 ? Instant.now() : null).build());
        }
    }

    private RoomEntity createRoom(MoveProjectEntity project, String name) {
        RoomEntity room = RoomEntity.builder().project(project).name(name).build();
        entityManager.persist(room);
        return room;
    }

    private BoxEntity createBox(MoveProjectEntity project, RoomEntity room, BoxStatus status,
                                boolean contentsDeferred, String... items) {
        BoxEntity box = BoxEntity.builder().project(project).room(room)
                .contentsDeferred(contentsDeferred).build();
        entityManager.persist(box);
        createEvent(project, ProjectEventType.BOX_CREATED, EventObjectType.BOX,
                box.getId(), "Создана коробка: " + room.getName());
        for (BoxStatus next : BoxStatus.values()) {
            if (next.ordinal() > 0 && next.ordinal() <= status.ordinal()) {
                box.changeStatus(next);
                createEvent(project, ProjectEventType.BOX_STATUS_CHANGED, EventObjectType.BOX,
                        box.getId(), "Статус коробки: " + next.name());
            }
        }
        for (String name : items) {
            entityManager.persist(ItemEntity.builder().box(box).name(name).build());
        }
        return box;
    }

    private void createTask(MoveProjectEntity project, String title, String description,
                            TaskStage stage, int dueDateOffset, String assignee, boolean completed) {
        MoveTaskEntity task = MoveTaskEntity.builder()
                .project(project).title(title).description(description).stage(stage)
                .dueDate(project.getMoveDate().plusDays(dueDateOffset)).assigneeName(assignee)
                .completedAt(completed ? Instant.now() : null).build();
        entityManager.persist(task);
        if (completed) {
            createEvent(project, ProjectEventType.TASK_COMPLETED, EventObjectType.TASK,
                    task.getId(), "Выполнена задача: " + title);
        }
    }

    private void createEvent(MoveProjectEntity project, ProjectEventType type,
                             EventObjectType objectType, UUID objectId, String description) {
        entityManager.persist(ProjectEventEntity.builder()
                .project(project).actor(project.getOwner()).eventType(type)
                .objectType(objectType).objectId(objectId).description(description).build());
    }
}
