package ru.example.prodbackend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.example.prodbackend.auth.service.RefreshTokenService;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.checklist.dto.request.CreateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.UpdateChecklistItemRequest;
import ru.example.prodbackend.checklist.service.FirstDayChecklistService;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.dto.request.AddProjectParticipantRequest;
import ru.example.prodbackend.move.repository.MoveProjectRepository;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.dto.request.CreateRoomRequest;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.task.dto.request.CreateMoveTaskRequest;
import ru.example.prodbackend.task.dto.request.MoveTaskFilter;
import ru.example.prodbackend.task.dto.request.UpdateMoveTaskRequest;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import ru.example.prodbackend.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@Profile({"dev", "prod"})
@Order(100)
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private static final String DEMO_PROJECT_NAME = "Переезд в новую квартиру";

    private final ItemService itemService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final MoveProjectRepository moveProjectRepository;
    private final MoveProjectService moveProjectService;
    private final RoomService roomService;
    private final BoxService boxService;
    private final MoveTaskService moveTaskService;
    private final FirstDayChecklistService checklistService;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Value("${USER1_PASSWORD}")
    private String user1Password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createUser("admin", adminPassword, "Алексей", "Админ", UserRole.ADMIN);
        UserEntity user = createUser("user1", user1Password, "Анна", "Пользователь", UserRole.USER);
        if (!moveProjectRepository.existsByOwner_IdAndName(user.getId(), DEMO_PROJECT_NAME)) createDemoProject(user);
        log.info("Demo data ready: admin, user1 and a complete demo move for user1");
    }

    private UserEntity createUser(String email, String password, String firstName, String lastName, UserRole role) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            var user = userRepository.saveAndFlush(UserEntity.builder().email(email).password(passwordEncoder.encode(password))
                    .firstName(firstName).lastName(lastName).role(role).build());
            refreshTokenService.createRefreshToken(user);
            return user;
        });
    }

    private void createDemoProject(UserEntity user) {
        LocalDate moveDate = LocalDate.now(ZoneOffset.UTC).plusDays(14);
        UUID adminId = userRepository.findByEmail("admin").orElseThrow().getId();
        var project = moveProjectService.createMoveProject(CreateMoveProjectRequest.builder().name(DEMO_PROJECT_NAME)
                .moveDate(moveDate.atStartOfDay(ZoneOffset.UTC).toInstant()).originAddress("Москва, ул. Лесная, д. 10, кв. 15")
                .destinationAddress("Москва, ул. Садовая, д. 25, кв. 42")
                .participants(List.of(AddProjectParticipantRequest.builder().userId(adminId).build())).build(), user);
        UUID projectId = project.getId();
        UUID kitchen = roomService.createRoom(projectId, CreateRoomRequest.builder().name("Кухня").iconKey("kitchen").build(), user.getId()).getId();
        UUID livingRoom = roomService.createRoom(projectId, CreateRoomRequest.builder().name("Гостиная").iconKey("sofa").build(), user.getId()).getId();
        UUID bedroom = roomService.createRoom(projectId, CreateRoomRequest.builder().name("Спальня").iconKey("bed").build(), user.getId()).getId();
        UUID bathroom = roomService.createRoom(projectId, CreateRoomRequest.builder().name("Ванная").iconKey("bath").build(), user.getId()).getId();

        createBox(user.getId(), projectId, livingRoom, 1, BoxStatus.PACKING, true);
        createBox(user.getId(), projectId, kitchen, 2, BoxStatus.PACKED, false, "Тарелки", "Кружки", "Столовые приборы");
        List<UUID> bedroomItems = createBox(user.getId(), projectId, bedroom, 3, BoxStatus.LOADED, false, "Постельное бельё", "Подушки", "Плед");
        createBox(user.getId(), projectId, livingRoom, 4, BoxStatus.DELIVERED, false, "Книги", "Настольная лампа", "Фоторамки");
        createBox(user.getId(), projectId, bathroom, 5, BoxStatus.UNPACKED, false, "Полотенца", "Шампунь", "Фен");

        for (var task : moveTaskService.findAll(projectId, new MoveTaskFilter(), new PaginationRequest(), user.getId()).getContent()) {
            if (task.getTitle().equals("Заказать грузчиков") || task.getTitle().equals("Отключить интернет")) {
                var update = new UpdateMoveTaskRequest();
                update.setAssigneeName("Анна");
                moveTaskService.update(projectId, task.getId(), update, user.getId());
                moveTaskService.changeCompletion(projectId, task.getId(), true, user.getId());
            }
        }
        moveTaskService.create(projectId, CreateMoveTaskRequest.builder().title("Упаковать кухню")
                .description("Обернуть хрупкую посуду и подписать коробки.").stage(TaskStage.BEFORE_MOVE)
                .dueDate(moveDate.minusDays(2)).assigneeName("Алексей").build(), user.getId());

        checklistService.create(projectId, CreateChecklistItemRequest.builder().name("Документы").build(), user.getId());
        checklistService.create(projectId, CreateChecklistItemRequest.builder().name("Аптечка").build(), user.getId());
        for (var item : checklistService.findAll(projectId, new PaginationRequest(), user.getId()).getContent()) {
            if (item.getName().equals("Постельное бельё")) checklistService.link(projectId, item.getId(), bedroomItems.get(0), user.getId());
            if (item.getName().equals("Зарядки") || item.getName().equals("Документы")) {
                checklistService.update(projectId, item.getId(), UpdateChecklistItemRequest.builder().completed(true).build(), user.getId());
            }
        }
    }

    private List<UUID> createBox(UUID userId, UUID projectId, UUID roomId, int number, BoxStatus status, boolean deferred, String... items) {
        String title = switch (number) {
            case 2 -> "Посуда для кухни";
            case 3 -> "Коробка первого дня";
            case 5 -> "Везу с собой";
            default -> "";
        };
        var box = boxService.create(userId, projectId, CreateBoxRequest.builder().roomId(roomId).number(number)
                .contentsDeferred(deferred).title(title).fragile(number == 2).firstDay(number == 3).carryWithMe(number == 5).build());
        List<UUID> itemIds = new ArrayList<>();
        for (String name : items) {
            int quantity = switch (name) {
                case "Тарелки", "Кружки" -> 6;
                case "Полотенца", "Подушки" -> 2;
                default -> 1;
            };
            boolean firstDay = name.equals("Постельное бельё") || name.equals("Полотенца");
            var item = itemService.create(userId, projectId, box.getId(), CreateItemRequest.builder()
                    .name(name).quantity(quantity).firstDay(firstDay).build());
            itemIds.add(item.getId());
            if (name.equals("Фен")) {
                var update = new UpdateItemRequest();
                update.setLost(true);
                itemService.update(userId, projectId, box.getId(), item.getId(), update);
            }
        }
        if (status != BoxStatus.PACKING) markItems(userId, projectId, box.getId(), itemIds, true);
        for (BoxStatus next : BoxStatus.values()) {
            if (next == BoxStatus.PACKING || next.ordinal() > status.ordinal()) continue;
            if (next == BoxStatus.UNPACKED) markItems(userId, projectId, box.getId(), itemIds, false);
            boxService.changeStatus(userId, projectId, box.getId(), next);
        }
        return itemIds;
    }

    private void markItems(UUID userId, UUID projectId, UUID boxId, List<UUID> itemIds, boolean packing) {
        for (UUID itemId : itemIds) {
            var update = new UpdateItemRequest();
            if (packing) update.setPacked(true);
            else update.setUnpacked(true);
            itemService.update(userId, projectId, boxId, itemId, update);
        }
    }
}
