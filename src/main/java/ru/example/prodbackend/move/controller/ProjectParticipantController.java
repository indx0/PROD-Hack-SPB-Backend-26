package ru.example.prodbackend.move.controller;

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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.move.dto.request.AddProjectParticipantRequest;
import ru.example.prodbackend.move.dto.response.ProjectParticipantResponse;
import ru.example.prodbackend.move.service.ProjectParticipantService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/move-projects/{projectId}/participants")
@Tag(name = "Участники переезда", description = "Существующие пользователи с равными правами; приглашения и подтверждения не требуются")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный UUID или JSON", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Требуется авторизация", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Проект недоступен; при добавлении — пользователь не найден", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ProjectParticipantController {
    private final ProjectParticipantService participantService;

    @GetMapping
    @Operation(summary = "Получить участников проекта", description = "Доступно любому участнику, в том числе в архиве. Создатель первый, остальные по UUID. creator — информация о создателе, права у всех одинаковые.")
    public ResponseEntity<List<ProjectParticipantResponse>> findAll(@PathVariable UUID projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(participantService.findAll(projectId, user.getId()));
    }

    @PostMapping
    @Operation(summary = "Добавить участника по почте или UUID", description = "Укажите ровно одно из email и userId. Любой участник активного проекта может добавить существующий аккаунт. Доступ появляется сразу, проект попадает в общий список добавленного пользователя. Повтор или добавление создателя возвращает существующую запись без дубликатов и изменения времени. Почта ищется точно, как в аккаунте, после удаления пробелов по краям.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Участник добавлен или уже имеет доступ", content = @Content(schema = @Schema(implementation = ProjectParticipantResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве; сначала восстановите его", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: укажите email или userId, но не оба; почта некорректна", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ProjectParticipantResponse> add(@PathVariable UUID projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user, @Valid @RequestBody AddProjectParticipantRequest request) {
        return ResponseEntity.ok(participantService.add(projectId, user.getId(), request));
    }
}
