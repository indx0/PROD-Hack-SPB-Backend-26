package ru.example.prodbackend.taskset.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.*;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.taskset.dto.*;
import ru.example.prodbackend.taskset.service.TaskSetService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/task-sets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = SwaggerTags.TASK_SETS)
@SecurityRequirement(name = "jwtAuth")
public class TaskSetController {
    private final TaskSetService service;

    @PostMapping
    @Operation(summary = "Создать личный набор", description = "Сохраняет набор и состав одной транзакцией. Порядок входного списка задаёт позиции от 0; названия по краям обрезаются. Названия наборов и задач могут повторяться.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Личный набор создан", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskSetResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Название обязательно, до 255 символов; описание до 10000; items содержит 1–100 задач с названием, этапом и offsetDays", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSetResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody SaveTaskSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user.getId(), request));
    }

    @GetMapping
    @Operation(summary = "Получить список личных наборов", description = "Без удалённых, createdAt DESC и id DESC; пагинация в БД. Возвращает сведения без состава; задачи доступны по GET /{setId}.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список получен"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректная пагинация: page >= 0, size от 1 до 100", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<TaskSetSummaryResponse>> findAll(@Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination) {
        return ResponseEntity.ok(service.findAll(user.getId(), pagination));
    }

    @GetMapping("/{setId}")
    @Operation(summary = "Получить свой набор с задачами", description = "Возвращает личный набор текущего пользователя с задачами в порядке позиций. Чужой или удалённый набор недоступен.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Запись получена"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "TASK_SET_NOT_FOUND: Набор отсутствует, удалён или принадлежит другому пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSetResponse> findById(@Parameter(description = "Идентификатор личного набора задач") @PathVariable UUID setId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findById(user.getId(), setId));
    }

    @PutMapping("/{setId}")
    @Operation(summary = "Заменить личный набор", description = "Нужны name и непустой items. Отсутствующее или null описание очищается. Состав заменяется целиком с новыми UUID элементов. Уже созданные задачи и прошлые применения не меняются.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Запись обновлена"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "TASK_SET_NOT_FOUND: Набор отсутствует, удалён или принадлежит другому пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Название обязательно, до 255 символов; описание до 10000; items содержит 1–100 задач с названием, этапом и offsetDays", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSetResponse> replace(@Parameter(description = "Идентификатор личного набора задач") @PathVariable UUID setId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Valid @RequestBody SaveTaskSetRequest request) {
        return ResponseEntity.ok(service.replace(user.getId(), setId, request));
    }

    @DeleteMapping("/{setId}")
    @Operation(summary = "Удалить свой набор", description = "Мягкое удаление через deletedAt: набор исчезает из каталога и запрещён для новых применений. Прежние задачи и история применений сохраняются.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Набор удалён", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "TASK_SET_NOT_FOUND: Набор отсутствует, удалён или принадлежит другому пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> delete(@Parameter(description = "Идентификатор личного набора задач") @PathVariable UUID setId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        service.delete(user.getId(), setId);
        return ResponseEntity.noContent().build();
    }
}
