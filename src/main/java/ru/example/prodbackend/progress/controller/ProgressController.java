package ru.example.prodbackend.progress.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.task.dto.response.TaskSummaryResponse;
import ru.example.prodbackend.task.service.MoveTaskService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/projects/{projectId}/progress", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Progress", description = "Сводка по коробкам и выполнению задач проекта")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: некорректный UUID",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: требуется действующий access токен",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не найден или недоступен",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
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
            responses = @ApiResponse(responseCode = "200", description = "Сводка по коробкам",
                    content = @Content(schema = @Schema(implementation = BoxSummaryResponse.class))))
    public ResponseEntity<BoxSummaryResponse> boxes(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(boxService.summary(user.getId(), projectId));
    }

    @GetMapping("/tasks")
    @Operation(summary = "Получить статистику выполнения задач",
            description = """
                    Возвращает totalTasks — общее количество задач проекта по всем этапам,
                    и completedTasks — количество задач с заполненным completedAt.
                    Учитывает начальные задачи и добавленные пользователем. Шаблоны задач и чек-лист
                    первого дня отдельно не учитываются. Для проекта без задач оба счётчика равны нулю.
                    Фильтры и пагинация не предусмотрены. Доступ разрешён любому участнику проекта.
                    Чтение не изменяет данные и не создаёт события.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Статистика выполнения задач",
                    content = @Content(schema = @Schema(implementation = TaskSummaryResponse.class))))
    public ResponseEntity<TaskSummaryResponse> tasks(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(moveTaskService.summary(projectId, user.getId()));
    }
}
