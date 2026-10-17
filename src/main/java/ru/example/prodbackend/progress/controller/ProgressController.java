package ru.example.prodbackend.progress.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.example.prodbackend.box.dto.BoxSummaryResponse;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.task.dto.response.TaskSummaryResponse;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping(value = "/api/projects/{projectId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = SwaggerTags.PROGRESS)
@SecurityRequirement(name = "jwtAuth")
public class ProgressController {
    private final BoxService boxService;
    private final MoveTaskService moveTaskService;

    @GetMapping("/boxes")
    @Operation(summary = "Получить сводку по коробкам",
            description = """
                    Возвращает общее количество коробок, счётчики всех статусов, количество коробок
                    с отложенным содержимым и прогресс распаковки по комнатам.
                    Ответ совпадает с GET /api/projects/{projectId}/boxes/summary.
                    Включает только комнаты с коробками. Для пустого проекта — нули и пустой список комнат.
                    Доступ разрешён любому участнику проекта. Чтение не изменяет данные и не создаёт события.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Сводка по коробкам",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BoxSummaryResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<BoxSummaryResponse> boxes(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(boxService.summary(user.getId(), projectId));
    }

    @GetMapping("/tasks")
    @Operation(summary = "Получить статистику выполнения задач",
            description = """
                    Возвращает totalTasks — общее количество задач проекта по всем этапам,
                    и completedTasks — количество задач с заполненным completedAt. Каждая реальная задача считается
                    ровно один раз независимо от папки; счётчики папок дополнительно не прибавляются.
                    Учитывает задачи, явно созданные пользователем, скопированные из наборов и перенесённые
                    из старого чек-листа первого дня. Шаблоны и архивные исходные пункты отдельно не учитываются. Для проекта без задач оба счётчика равны нулю.
                    Фильтры и пагинация не предусмотрены. Доступ разрешён любому участнику проекта.
                    Чтение не изменяет данные и не создаёт события.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Статистика выполнения задач",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = TaskSummaryResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSummaryResponse> tasks(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(moveTaskService.summary(projectId, user.getId()));
    }
}
