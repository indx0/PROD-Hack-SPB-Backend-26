package ru.example.prodbackend.task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.task.dto.request.*;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.dto.response.TaskTemplateResponse;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = SwaggerTags.TASKS)
public class MoveTaskController {

    private final MoveTaskService moveTaskService;

    @PostMapping
    @Operation(summary = "Создать задачу",
            description = "Создаёт невыполненную задачу доступного проекта. Без dueType используется FIXED, без dueDate — без срока. RELATIVE требует offsetDays и запрещает dueDate; срок считает сервер от даты переезда в UTC. FIXED запрещает offsetDays. Противоречия — 422. Ответственного можно назначить по assigneeId участника, включая создателя: ID имеет приоритет над свободным assigneeName, явный null снимает назначение. Без assigneeId свободное имя снимает связь с аккаунтом. Чужой ID — 422 без изменений. folderId и linkedItemId необязательны: отсутствие/null означает без папки/связи. Папка и вещь должны принадлежать этому переезду; ошибка откатывает создание. Связь не меняет item.firstDay или упаковку вещи.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Задача создана",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_FOLDER_NOT_FOUND: Папка отсутствует или принадлежит другому переезду; NOT_FOUND: Связанная вещь отсутствует или принадлежит другому переезду", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Название пустое или длиннее 255 символов, этап отсутствует, описание длиннее 10000 или ответственный длиннее 255 символов или assigneeId не принадлежит участнику переезда",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> create(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Valid @RequestBody CreateMoveTaskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveTaskService.create(projectId, request, user.getId()));
    }

    @GetMapping
    @Operation(summary = "Получить задачи проекта",
            description = "Фильтры folderId, stage и completed объединяются через AND. Без folderId возвращаются все задачи проекта, включая задачи без папки. Чужая или отсутствующая папка — 404 TASK_FOLDER_NOT_FOUND. Сортировка createdAt ASC, id ASC. По умолчанию page=0, size=20; пустая страница возвращает 200.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница задач"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_FOLDER_NOT_FOUND: Папка отсутствует или принадлежит другому переезду", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректные фильтры или пагинация; page >= 0, size от 1 до 100",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<MoveTaskResponse>> findAll(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Valid @ModelAttribute @ParameterObject MoveTaskFilter filter,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findAll(projectId, filter, pagination, user.getId()));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Получить задачу", description = "Возвращает задачу доступного участнику проекта со сроком, ответственным, выполнением и текущей папкой. linkedItemId и актуальные linkedItemName/boxId/boxNumber/roomId/roomName/roomIconKey отражают текущее расположение вещи; без связи поля равны null. Название задачи независимо от имени вещи; удаление вещи очищает связь, сохраняя задачу.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Карточка задачи",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_NOT_FOUND: Задачи нет в этом проекте", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> findById(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор задачи проекта") @PathVariable UUID taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findById(projectId, taskId, user.getId()));
    }

    @PatchMapping("/{taskId}")
    @Operation(summary = "Изменить задачу",
            description = "Отсутствующие поля сохраняются. Старый запрос с dueDate устанавливает FIXED и очищает offsetDays; dueDate:null снимает срок. RELATIVE запрещает dueDate, требует offsetDays при первом включении, иначе сохраняет текущее смещение. Изменение только offsetDays допустимо для RELATIVE. FIXED без dueDate сохраняет текущую дату. null для dueType и offsetDays запрещён. null очищает description и assigneeName. Пустой объект, неизвестные поля и completed запрещены; выполнение меняется отдельным запросом. Противоречивый срок — 422. Ответственного можно назначить по assigneeId участника, включая создателя: ID имеет приоритет над свободным assigneeName, явный null снимает назначение. Без assigneeId свободное имя снимает связь с аккаунтом. Чужой ID — 422 без изменений. Для folderId и linkedItemId отсутствие сохраняет значение, явный null очищает. Перенос меняет только папку, сохраняя UUID, выполнение, сроки, ответственного и шаблонное происхождение. Чужая/отсутствующая папка или вещь — 404, весь PATCH откатывается; item.firstDay и упаковка не меняются.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Задача обновлена",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_NOT_FOUND: Задачи нет в этом проекте; TASK_FOLDER_NOT_FOUND: Папка отсутствует или принадлежит другому переезду; NOT_FOUND: Связанная вещь отсутствует или принадлежит другому переезду", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Пустой запрос, пустое название, превышение допустимой длины или assigneeId не принадлежит участнику переезда",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> update(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор задачи проекта") @PathVariable UUID taskId,
            @Valid @RequestBody UpdateMoveTaskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.update(projectId, taskId, request, user.getId()));
    }

    @PatchMapping("/{taskId}/completion")
    @Operation(summary = "Изменить выполнение задачи",
            description = "completed=true выполняет задачу и записывает TASK_COMPLETED в той же транзакции. false возвращает в работу. Повтор текущего значения не создаёт событие и не меняет updatedAt/completedAt. Выполнение связанной задачи не меняет packed/unpacked, item.firstDay или статус коробки.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Задача с актуальным состоянием",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_NOT_FOUND: Задачи нет в этом проекте", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: completed обязателен",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> changeCompletion(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор задачи проекта") @PathVariable UUID taskId,
            @Valid @RequestBody ChangeTaskCompletionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.changeCompletion(projectId, taskId, request.getCompleted(), user.getId()));
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Удалить задачу",
            description = "Удаляет задачу своего проекта. Подтверждение выполняется интерфейсом. Повторное удаление возвращает 404; история событий сохраняется.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Задача удалена", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; TASK_NOT_FOUND: Задачи нет в этом проекте", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> delete(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор задачи проекта") @PathVariable UUID taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        moveTaskService.delete(projectId, taskId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    @Operation(summary = "Получить готовые шаблоны задач",
            description = "Общие и свои личные готовые задачи, без удалённых. Чужие личные образцы скрыты даже от участников проекта; personal=true обозначает редактируемый личный образец. Каталог без выбора проекта также доступен через GET /api/task-templates. Можно отфильтровать по stage. Сортировка title ASC, id ASC; общая пагинация.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница шаблонов"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректная пагинация; page >= 0, size от 1 до 100",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<TaskTemplateResponse>> findTemplates(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Этап переезда; отсутствие параметра означает все этапы") @RequestParam(required = false) TaskStage stage,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findTemplates(projectId, stage, pagination, user.getId()));
    }

    @PostMapping("/from-template")
    @Operation(summary = "Создать задачу из шаблона",
            description = "Копирует название, описание, этап и смещение общего или своего личного образца в новую RELATIVE-задачу. Чужой или удалённый образец — 404. Последующее изменение или удаление образца не меняет созданную задачу. Срок = дата переезда UTC + offsetDays; смещение сохраняется у задачи независимо от шаблона. Можно переопределить offsetDays или явно выбрать FIXED и dueDate; старый запрос только с dueDate также означает FIXED. dueDate:null задаёт FIXED без срока. Противоречивый срок — 422. Один шаблон применяется один раз, пока соответствующая задача существует. Ответственного можно назначить по assigneeId участника, включая создателя: ID имеет приоритет над свободным assigneeName, явный null снимает назначение. Без assigneeId свободное имя снимает связь с аккаунтом. Чужой ID — 422 без изменений. Необязательный folderId назначает папку этого переезда; отсутствие/null — без папки. Чужая/отсутствующая папка — 404 TASK_FOLDER_NOT_FOUND без создания задачи. Ответ подтверждает текущий folderId.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Задача создана из шаблона",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект недоступен; TASK_TEMPLATE_NOT_FOUND: Шаблон не найден; TASK_FOLDER_NOT_FOUND: Папка отсутствует или принадлежит другому переезду",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Задача из этого шаблона уже существует в проекте; для изменений — переезд в архиве, сначала восстановите его",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: templateId отсутствует или ответственный длиннее 255 символов или assigneeId не принадлежит участнику переезда",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> applyTemplate(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Valid @RequestBody ApplyTaskTemplateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveTaskService.applyTemplate(projectId, request, user.getId()));
    }
}
