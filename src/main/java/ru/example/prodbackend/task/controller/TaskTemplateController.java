package ru.example.prodbackend.task.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.*;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.task.dto.request.SaveTaskTemplateRequest;
import ru.example.prodbackend.task.dto.response.TaskTemplateResponse;
import ru.example.prodbackend.task.entity.TaskStage;
import ru.example.prodbackend.task.service.TaskTemplateService;
import ru.example.prodbackend.user.entity.UserEntity;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/task-templates", produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Готовые задачи", description = "Общие образцы и личные готовые задачи аккаунта. Сохранение в каталог не добавляет задачу в переезд")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный JSON, UUID или enum", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "TASK_TEMPLATE_NOT_FOUND: образец отсутствует, удалён или недоступен; менять и удалять можно только личные образцы", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: title обязателен, до 255 символов; description до 10000; stage и dueDateOffsetDays обязательны; либо неверная пагинация", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TaskTemplateController {
    private final TaskTemplateService service;

    @GetMapping
    @Operation(summary = "Получить каталог готовых задач", description = "Общие и свои неудалённые образцы; личные отмечены personal=true. Не требует проекта. stage — необязательный фильтр, title ASC и id ASC; page=0, size=20 (до 100). Чужие личные образцы скрыты, в том числе от участников совместного проекта.")
    public ResponseEntity<PageResponse<TaskTemplateResponse>> findAll(@RequestParam(required = false) TaskStage stage,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findAll(user.getId(), stage, pagination));
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Получить готовую задачу")
    public ResponseEntity<TaskTemplateResponse> findById(@PathVariable UUID templateId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findById(user.getId(), templateId));
    }

    @PostMapping
    @Operation(summary = "Сохранить личную готовую задачу", description = "Сохраняет отдельный образец в аккаунте, доступный после нового входа. Не создаёт задачу плана и не меняет переезды. Одинаковые названия допустимы. Применение — POST /api/move-projects/{projectId}/tasks/from-template с templateId.",
            responses = @ApiResponse(responseCode = "201", description = "Личная готовая задача сохранена", content = @Content(schema = @Schema(implementation = TaskTemplateResponse.class))))
    public ResponseEntity<TaskTemplateResponse> create(@Valid @RequestBody SaveTaskTemplateRequest request, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), request));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Заменить личную готовую задачу", description = "Нужны title, stage и dueDateOffsetDays. Отсутствующее или null описание очищается. Уже добавленные в переезды задачи сохраняют свои поля, сроки, выполнение и ответственного. Общие образцы менять нельзя.")
    public ResponseEntity<TaskTemplateResponse> replace(@PathVariable UUID templateId, @Valid @RequestBody SaveTaskTemplateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.replace(user.getId(), templateId, request));
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "Удалить личную готовую задачу", description = "Скрывает образец и запрещает новые применения, сохраняя уже созданные задачи. Общие образцы удалить нельзя.",
            responses = @ApiResponse(responseCode = "204", description = "Образец удалён", content = @Content))
    public ResponseEntity<Void> delete(@PathVariable UUID templateId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        service.delete(user.getId(), templateId);
        return ResponseEntity.noContent().build();
    }
}
