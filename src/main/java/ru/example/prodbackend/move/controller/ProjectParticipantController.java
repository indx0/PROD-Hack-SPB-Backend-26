package ru.example.prodbackend.move.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.move.dto.request.AddProjectParticipantRequest;
import ru.example.prodbackend.move.dto.response.ProjectParticipantResponse;
import ru.example.prodbackend.move.service.ProjectParticipantService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/move-projects/{projectId}/participants")
@Tag(name = SwaggerTags.PARTICIPANTS)
@SecurityRequirement(name = "jwtAuth")
public class ProjectParticipantController {
    private final ProjectParticipantService participantService;

    @GetMapping
    @Operation(summary = "Получить участников проекта", description = "Доступно любому участнику, в том числе в архиве. Создатель первый, остальные по UUID. creator — информация о создателе, права у всех одинаковые.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список получен"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<List<ProjectParticipantResponse>> findAll(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(participantService.findAll(projectId, user.getId()));
    }

    @PostMapping
    @Operation(summary = "Добавить участника по почте или UUID", description = "Укажите ровно одно из email и userId. Любой участник активного проекта может добавить существующий аккаунт. Доступ появляется сразу, проект попадает в общий список добавленного пользователя. Повтор или добавление создателя возвращает существующую запись без дубликатов и изменения времени. Почта ищется точно, как в аккаунте, после удаления пробелов по краям.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Участник добавлен или уже имеет доступ", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProjectParticipantResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен; NOT_FOUND: Пользователь не найден", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Укажите email или userId, но не оба; почта некорректна", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ProjectParticipantResponse> add(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user, @Valid @RequestBody AddProjectParticipantRequest request) {
        return ResponseEntity.ok(participantService.add(projectId, user.getId(), request));
    }
}
