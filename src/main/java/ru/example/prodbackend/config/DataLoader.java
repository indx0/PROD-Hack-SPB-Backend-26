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
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.entity.BoxStatus;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.entity.ItemFlag;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.move.dto.request.AddProjectParticipantRequest;
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.room.dto.request.CreateRoomRequest;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.task.dto.request.CreateMoveTaskRequest;
import ru.example.prodbackend.task.dto.request.MoveTaskFilter;
import ru.example.prodbackend.task.dto.request.UpdateMoveTaskRequest;
import ru.example.prodbackend.task.entity.TaskDueType;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.taskfolder.dto.CreateTaskFolderRequest;
import ru.example.prodbackend.taskset.dto.InitialTaskSetApplicationRequest;
import ru.example.prodbackend.taskset.dto.SaveTaskSetRequest;
import ru.example.prodbackend.taskset.dto.TaskSetItemRequest;
import ru.example.prodbackend.taskset.service.TaskSetService;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.entity.UserRole;
import ru.example.prodbackend.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@Profile({"dev", "prod"})
@Order(100)
@RequiredArgsConstructor
public class DataLoader implements ApplicationRunner {

    private final ItemService itemService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MoveProjectService moveProjectService;
    private final RoomService roomService;
    private final BoxService boxService;
    private final MoveTaskService moveTaskService;
    private final TaskSetService taskSetService;

    @Value("${ADMIN_PASSWORD}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createUser("admin", adminPassword, UserRole.ADMIN);
        List<UserEntity> users = new ArrayList<>();
        Map<UUID, UUID> newUserTaskSets = new HashMap<>();
        for (int number = 1; number <= 10; number++) {
            String login = "u" + number;
            var existing = userRepository.findByEmail(login);
            var user = existing.orElseGet(() -> createUser(login, "1", UserRole.USER));
            users.add(user);
            // Seed only new accounts: restarts preserve judges' edits, renames and deletions.
            if (existing.isEmpty()) newUserTaskSets.put(user.getId(), createOfficeTaskSet(user.getId()));
        }

        for (var user : users) {
            UUID setId = newUserTaskSets.get(user.getId());
            if (setId == null) continue;
            createProject(user, List.of(), setId, "Переезд IT-отдела — " + user.getEmail(), false, false);
            createProject(user, List.of(), setId, "Перенос архива — " + user.getEmail(), true, false);
        }
        createSharedProject(users.subList(0, 5), newUserTaskSets, "Общий переезд офиса из Феррума");
        createSharedProject(users.subList(5, 10), newUserTaskSets, "Общий переезд офиса на Неве");
        log.info("Office demo data ready: {} new demo accounts; existing accounts and data preserved", newUserTaskSets.size());
    }

    private UserEntity createUser(String login, String password, UserRole role) {
        return userRepository.findByEmail(login).orElseGet(() -> userRepository.saveAndFlush(UserEntity.builder()
                .email(login).firstName(login).password(passwordEncoder.encode(password)).role(role).build()));
    }

    private UUID createOfficeTaskSet(UUID userId) {
        return taskSetService.create(userId, new SaveTaskSetRequest("Переезд офиса", "Небольшой готовый план для отдела",
                List.of(new TaskSetItemRequest("Подготовить опись отдела", "Согласовать список оборудования и документов.", TaskStage.BEFORE_MOVE, -2),
                        new TaskSetItemRequest("Проверить погрузку коробок", "Сверить номера коробок перед отправлением.", TaskStage.MOVING_DAY, 0),
                        new TaskSetItemRequest("Разместить вещи по комнатам", "Распаковать рабочие места и проверить опись.", TaskStage.AFTER_MOVE, 1))))
                .getId();
    }

    private void createSharedProject(List<UserEntity> team, Map<UUID, UUID> taskSets, String name) {
        var owner = team.get(0);
        UUID setId = taskSets.get(owner.getId());
        if (setId != null) createProject(owner, team.subList(1, team.size()), setId, name, false, true);
    }

    private void createProject(UserEntity owner, List<UserEntity> participants, UUID setId, String name,
                               boolean archiveMove, boolean shared) {
        UUID userId = owner.getId();
        UUID planFolder = UUID.randomUUID();
        UUID inventoryFolder = UUID.randomUUID();
        LocalDate moveDate = LocalDate.now(ZoneOffset.UTC).plusDays(shared ? 0 : archiveMove ? -2 : 5);
        var project = moveProjectService.createMoveProject(CreateMoveProjectRequest.builder().name(name)
                .moveDate(moveDate.atTime(9, 0).toInstant(ZoneOffset.UTC))
                .originAddress("Демо-офис, корпус А").destinationAddress("Демо-офис, корпус Б")
                .participants(participants.stream().map(user -> AddProjectParticipantRequest.builder().userId(user.getId()).build()).toList())
                .folders(List.of(new CreateTaskFolderRequest(planFolder, "Организация", "mint"),
                        new CreateTaskFolderRequest(inventoryFolder, archiveMove ? "Документы" : "Оборудование", "cyan")))
                .taskSetApplications(List.of(new InitialTaskSetApplicationRequest(setId, planFolder))).build(), owner);
        UUID projectId = project.getId();
        UUID workRoom = roomService.createRoom(projectId, CreateRoomRequest.builder()
                .name(archiveMove ? "Архив" : "Рабочая зона").iconKey("book").build(), userId).getId();
        UUID meetingRoom = roomService.createRoom(projectId, CreateRoomRequest.builder()
                .name(archiveMove ? "Кабинет" : "Переговорная").iconKey(archiveMove ? "book" : "sofa").build(), userId).getId();

        var editableBox = archiveMove
                ? createBox(userId, projectId, workRoom, 1, "Документы отдела", BoxStatus.PACKING,
                    item("Папка с договорами", 1, ItemFlag.CARRY_PERSONALLY), item("Реестр документов", 1))
                : createBox(userId, projectId, workRoom, 1, "Рабочее место", BoxStatus.PACKING,
                    item("Ноутбук", 1, ItemFlag.FRAGILE, ItemFlag.FIRST_DAY, ItemFlag.CARRY_PERSONALLY),
                    item("HDMI-переходник", 1, ItemFlag.FIRST_DAY));
        markItems(userId, projectId, editableBox.id(), editableBox.itemIds().subList(0, 1), true);

        if (archiveMove) {
            createBox(userId, projectId, meetingRoom, 2, "Архивные папки", BoxStatus.DELIVERED,
                    item("Папка с договорами", 2), item("Канцелярский набор", 3));
        } else {
            createBox(userId, projectId, meetingRoom, 2, "Техника переговорной", BoxStatus.PACKED,
                    item("Монитор", 2, ItemFlag.FRAGILE), item("HDMI-переходник", 2));
        }
        createBox(userId, projectId, workRoom, 3, "Опись добавим позже", BoxStatus.PACKING);
        if (shared) {
            createBox(userId, projectId, workRoom, 4, "Сетевое оборудование", BoxStatus.LOADED,
                    item("Роутер", 1, ItemFlag.FIRST_DAY), item("Сетевой кабель", 3));
            createBox(userId, projectId, meetingRoom, 5, "Оборудование для встреч", BoxStatus.DELIVERED,
                    item("Проектор", 1, ItemFlag.FRAGILE), item("Пульт проектора", 1));
            createBox(userId, projectId, workRoom, 6, "Периферия сотрудников", BoxStatus.UNPACKED,
                    item("Клавиатура", 3), item("Мышь", 3));
        }

        List<UserEntity> team = new ArrayList<>(List.of(owner));
        team.addAll(participants);
        var tasks = moveTaskService.findAll(projectId, new MoveTaskFilter(), new PaginationRequest(), userId).getContent();
        for (int index = 0; index < tasks.size(); index++) {
            var task = tasks.get(index);
            var update = new UpdateMoveTaskRequest();
            update.setAssigneeId(team.get(index % team.size()).getId());
            if (archiveMove) {
                update.setTitle(switch (task.getStage()) {
                    case BEFORE_MOVE -> "Согласовать список архивных папок";
                    case MOVING_DAY -> "Передать документы в новый кабинет";
                    case AFTER_MOVE -> "Сверить архив с описью";
                });
            } else if (shared) {
                update.setTitle(switch (task.getStage()) {
                    case BEFORE_MOVE -> "Согласовать переезд с отделами";
                    case MOVING_DAY -> "Сверить погрузку по комнатам";
                    case AFTER_MOVE -> "Проверить готовность рабочих мест";
                });
            }
            moveTaskService.update(projectId, task.getId(), update, userId);
            if (task.getStage() == TaskStage.BEFORE_MOVE || archiveMove && task.getStage() == TaskStage.MOVING_DAY) {
                moveTaskService.changeCompletion(projectId, task.getId(), true, userId);
            }
        }
        UUID linkedItem = editableBox.itemIds().get(archiveMove ? 0 : 1);
        if (archiveMove || shared) {
            var lost = new UpdateItemRequest();
            lost.setLost(true);
            itemService.update(userId, projectId, editableBox.id(), linkedItem, lost);
        }
        moveTaskService.create(projectId, CreateMoveTaskRequest.builder()
                .title(archiveMove ? "Найти папку с договорами" : "Проверить HDMI-переходник")
                .description("Откройте связанную вещь; задачу и опись можно изменить самостоятельно.")
                .folderId(inventoryFolder).linkedItemId(linkedItem).assigneeId(team.get(team.size() - 1).getId())
                .stage(TaskStage.BEFORE_MOVE).dueType(TaskDueType.RELATIVE).offsetDays(-1).build(), userId);
    }

    private DemoBox createBox(UUID userId, UUID projectId, UUID roomId, int number, String title, BoxStatus status,
                               CreateItemRequest... items) {
        var content = List.of(items);
        var box = boxService.create(userId, projectId, CreateBoxRequest.builder().roomId(roomId).number(number).title(title)
                .contentsDeferred(content.isEmpty())
                .fragile(content.stream().anyMatch(item -> item.getFlags().contains(ItemFlag.FRAGILE)))
                .firstDay(content.stream().anyMatch(item -> item.getFlags().contains(ItemFlag.FIRST_DAY)))
                .carryWithMe(content.stream().anyMatch(item -> item.getFlags().contains(ItemFlag.CARRY_PERSONALLY))).build());
        List<UUID> itemIds = content.stream().map(item -> itemService.create(userId, projectId, box.getId(), item).getId()).toList();
        if (status != BoxStatus.PACKING) markItems(userId, projectId, box.getId(), itemIds, true);
        for (BoxStatus next : List.of(BoxStatus.PACKED, BoxStatus.LOADED, BoxStatus.DELIVERED, BoxStatus.UNPACKED)) {
            if (next.ordinal() > status.ordinal()) break;
            if (next == BoxStatus.UNPACKED) markItems(userId, projectId, box.getId(), itemIds, false);
            boxService.changeStatus(userId, projectId, box.getId(), next);
        }
        return new DemoBox(box.getId(), itemIds);
    }

    private CreateItemRequest item(String name, int quantity, ItemFlag... flags) {
        var selected = List.of(flags);
        return CreateItemRequest.builder().name(name).quantity(quantity).flags(selected)
                .firstDay(selected.contains(ItemFlag.FIRST_DAY)).build();
    }

    private void markItems(UUID userId, UUID projectId, UUID boxId, List<UUID> itemIds, boolean packing) {
        for (UUID itemId : itemIds) {
            var update = new UpdateItemRequest();
            if (packing) update.setPacked(true);
            else update.setUnpacked(true);
            itemService.update(userId, projectId, boxId, itemId, update);
        }
    }

    private record DemoBox(UUID id, List<UUID> itemIds) { }
}
