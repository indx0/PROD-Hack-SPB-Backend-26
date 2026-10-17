package ru.example.prodbackend.taskset.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.taskset.dto.TaskSetApplicationResponse;
import ru.example.prodbackend.taskset.dto.ApplyTaskSetRequest;
import ru.example.prodbackend.taskset.service.TaskSetService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/move-projects/{projectId}", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = SwaggerTags.TASK_SETS)
@SecurityRequirement(name = "jwtAuth")
public class TaskSetApplicationController {
    private final TaskSetService service;

    @GetMapping("/task-set-applications")
    @Operation(summary = "Получить сохранённые применения шаблонов переезда", description = """
            Только чтение: не применяет шаблоны, не создаёт задачи или папки и не восстанавливает удалённые задачи.
            Возвращает все применения проекта, включая шаблоны других участников и удалённые из личного каталога.
            Доступ разрешён создателю и любому участнику, включая чтение архивного переезда.
            Пагинация page >= 0, size 1–100; по умолчанию page=0 и size=20. Порядок appliedAt ASC, id ASC.
            Пустой список и страница за пределами списка возвращаются с 200 и обычными полями пагинации.
            Верхнеуровневый folderId — неизменяемый исторический UUID первоначальной папки, даже после её удаления.
            tasks содержит актуальные оставшиеся задачи в исходном порядке; tasks[].folderId показывает текущее
            положение после переносов и удаления папок. Название, выполнение, ответственные и связи с вещами актуальны.
            Позволяет проверить результат создания переезда без повторного POST и без изменяющего PUT.
            """, responses = {
                    @ApiResponse(responseCode = "200", description = "Страница сохранённых применений с актуальными оставшимися задачами"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат UUID проекта", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректный формат пагинации, page меньше 0 либо size вне диапазона 1–100", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<TaskSetApplicationResponse>> findAll(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findApplications(user.getId(), projectId, pagination));
    }

    @PutMapping("/task-sets/{setId}")
    @Operation(summary = "Применить свой шаблон задач к доступному переезду", description = """
            Необязательное тело {folderId: UUID|null} выбирает существующую папку этого переезда; null — без папки.
            Без тела или поля первое применение остаётся без папки, повтор сохраняет первоначальное назначение.
            Новую папку сначала создаёт POST task-folders отдельным запросом: при ошибке применения она остаётся пустой.
            Первое применение атомарно копирует весь набор в RELATIVE-задачи без выполнения и ответственного.
            Набор доступен только владельцу; проект — создателю и участникам. Пара проект/набор уникальна.
            Верхнеуровневый folderId — неизменяемый UUID первоначального назначения, сохраняемый после удаления папки.
            Текущие folderId задач могут отличаться после ручных переносов. Повтор возвращает ту же запись применения
            и актуальные оставшиеся задачи в исходном порядке; не восстанавливает удалённые и не сбрасывает изменения.
            Другое явно указанное первоначальное назначение — 409 TASK_SET_ALREADY_APPLIED; перемещение выполняет PATCH задач.
            Повтор с прежним folderId допустим после удаления папки, без её восстановления. Переименование, замена состава
            и мягкое удаление набора не меняют созданные задачи; после удаления набора разрешён прежний повтор, новое — 404.
            Архив запрещает любые применения, включая повторы.
            """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Новое или прежнее применение и оставшиеся задачи", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskSetApplicationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса; INVALID_REQUEST_BODY: Некорректный JSON, UUID или неизвестное поле тела", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект недоступен; TASK_SET_NOT_FOUND: Набор чужой, отсутствует или удалён до нового применения; TASK_FOLDER_NOT_FOUND: Папка отсутствует или принадлежит другому переезду при первом применении", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Проект в архиве; TASK_SET_ALREADY_APPLIED: Для прежнего применения явно указано иное первоначальное назначение", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Вычисляемая дата выходит за диапазон", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSetApplicationResponse> apply(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор личного набора задач") @PathVariable UUID setId,
            @Valid @RequestBody(required = false) ApplyTaskSetRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.apply(user.getId(), projectId, setId, request));
    }
}
