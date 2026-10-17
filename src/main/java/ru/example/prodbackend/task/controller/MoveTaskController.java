package ru.example.prodbackend.task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.task.dto.request.*;
import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.dto.response.TaskTemplateResponse;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Задачи переезда", description = "Задачи проекта и готовые шаблоны")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный UUID, JSON или формат параметра",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректен или истёк",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не найден или недоступен; для операций с taskId — TASK_NOT_FOUND: задачи нет в этом проекте",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class MoveTaskController {

    private final MoveTaskService moveTaskService;

    @PostMapping
    @Operation(summary = "Создать задачу",
            description = "Создаёт невыполненную задачу в своём проекте. Описание, срок и ответственный необязательны.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Задача создана",
                            content = @Content(schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве; сначала восстановите его", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов, этап отсутствует, описание длиннее 10000 или ответственный длиннее 255 символов",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMoveTaskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveTaskService.create(projectId, request, user.getId()));
    }

    @GetMapping
    @Operation(summary = "Получить задачи проекта",
            description = "Фильтры stage и completed объединяются через AND. Сортировка createdAt ASC, id ASC. По умолчанию page=0, size=20; пустая страница возвращает 200.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница задач"),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: некорректные фильтры или пагинация; page >= 0, size от 1 до 100",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<MoveTaskResponse>> findAll(
            @PathVariable UUID projectId,
            @Valid @ModelAttribute @ParameterObject MoveTaskFilter filter,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findAll(projectId, filter, pagination, user.getId()));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Получить задачу",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Карточка задачи",
                            content = @Content(schema = @Schema(implementation = MoveTaskResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> findById(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findById(projectId, taskId, user.getId()));
    }

    @PatchMapping("/{taskId}")
    @Operation(summary = "Изменить задачу",
            description = "Отсутствующие поля сохраняются. null очищает description, dueDate и assigneeName. null для title и stage, неизвестные поля и completed запрещены. Пустой объект запрещён; выполнение меняется отдельным запросом.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Задача обновлена",
                            content = @Content(schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве; сначала восстановите его", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: пустой запрос, пустое название или превышение допустимой длины",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> update(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateMoveTaskRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.update(projectId, taskId, request, user.getId()));
    }

    @PatchMapping("/{taskId}/completion")
    @Operation(summary = "Изменить выполнение задачи",
            description = "completed=true выполняет задачу и записывает TASK_COMPLETED в той же транзакции. false возвращает в работу. Повтор текущего значения не создаёт событие и не меняет updatedAt/completedAt.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Задача с актуальным состоянием",
                            content = @Content(schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве; сначала восстановите его", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: completed обязателен",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> changeCompletion(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody ChangeTaskCompletionRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.changeCompletion(projectId, taskId, request.getCompleted(), user.getId()));
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Удалить задачу",
            description = "Удаляет задачу своего проекта. Подтверждение выполняется интерфейсом. Повторное удаление возвращает 404; история событий сохраняется.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Задача удалена", content = @Content),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве; сначала восстановите его", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> delete(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        moveTaskService.delete(projectId, taskId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    @Operation(summary = "Получить готовые шаблоны задач",
            description = "Общий локальный каталог, доступный владельцу проекта. Можно отфильтровать по stage. Сортировка title ASC, id ASC; общая пагинация.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница шаблонов"),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: некорректная пагинация; page >= 0, size от 1 до 100",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<TaskTemplateResponse>> findTemplates(
            @PathVariable UUID projectId,
            @RequestParam(required = false) TaskStage stage,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveTaskService.findTemplates(projectId, stage, pagination, user.getId()));
    }

    @PostMapping("/from-template")
    @Operation(summary = "Создать задачу из шаблона",
            description = "Копирует название, описание и этап шаблона в новую невыполненную задачу. Срок и ответственного задаёт клиент. Один шаблон применяется один раз, пока соответствующая задача существует.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Задача создана из шаблона",
                            content = @Content(schema = @Schema(implementation = MoveTaskResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; TASK_TEMPLATE_NOT_FOUND: шаблон не найден",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: задача из этого шаблона уже существует в проекте; для изменений — переезд в архиве, сначала восстановите его",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: templateId отсутствует или ответственный длиннее 255 символов",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveTaskResponse> applyTemplate(
            @PathVariable UUID projectId,
            @Valid @RequestBody ApplyTaskTemplateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveTaskService.applyTemplate(projectId, request, user.getId()));
    }
}
