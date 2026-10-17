package ru.example.prodbackend.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.event.dto.ProjectEventResponse;
import ru.example.prodbackend.event.service.ProjectEventService;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/events", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Журнал проекта")
public class ProjectEventController {

    private final ProjectEventService eventService;

    @GetMapping
    @Operation(summary = "Получить журнал своего проекта", description = "Новые события первыми: occurredAt DESC, id DESC. page от 0, size от 1 до 100; по умолчанию 0 и 20. BOX_STATUS_CORRECTED содержит previousStatus и newStatus; у остальных событий они null. Удаление объекта не удаляет его историю. Email, пароли и адреса в ответ не включаются.", responses = {
            @ApiResponse(responseCode = "200", description = "Страница событий"),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: некорректный UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует или недействителен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не найден или недоступен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: некорректная пагинация", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ProjectEventResponse>> findAll(@PathVariable UUID projectId, @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
                                                                     @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(eventService.findAll(projectId, pagination, user.getId()));
    }
}
