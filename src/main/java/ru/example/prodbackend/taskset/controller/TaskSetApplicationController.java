package ru.example.prodbackend.taskset.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.taskset.dto.TaskSetApplicationResponse;
import ru.example.prodbackend.taskset.service.TaskSetService;
import ru.example.prodbackend.user.entity.UserEntity;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/move-projects/{projectId}/task-sets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Личные наборы задач")
@SecurityRequirement(name = "jwtAuth")
public class TaskSetApplicationController {
    private final TaskSetService service;

    @PutMapping("/{setId}")
    @Operation(summary = "Применить свой набор к доступному переезду", description = "Тело не требуется. Копирует весь набор в RELATIVE-задачи без выполнения и ответственного. Набор доступен только своему владельцу; проект — создателю и участникам. Пара проект/набор уникальна. Повтор возвращает ту же запись применения и актуальные оставшиеся задачи в исходном порядке, без восстановления удалённых. Переименование, замена состава и мягкое удаление набора не меняют созданные задачи; после удаления разрешён повтор прежнего применения, новое — 404. Архив запрещает любые применения, включая повторы.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Новое или прежнее применение и оставшиеся задачи", content = @Content(schema = @Schema(implementation = TaskSetApplicationResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректный UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; TASK_SET_NOT_FOUND: набор чужой, отсутствует или удалён до нового применения", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: проект в архиве", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: вычисляемая дата выходит за диапазон", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<TaskSetApplicationResponse> apply(@PathVariable UUID projectId, @PathVariable UUID setId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.apply(user.getId(), projectId, setId));
    }
}
