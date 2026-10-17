package ru.example.prodbackend.checklist.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.checklist.dto.request.CreateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.LinkChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.UpdateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;
import ru.example.prodbackend.checklist.service.FirstDayChecklistService;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/first-day-checklist", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = SwaggerTags.CHECKLIST)
public class FirstDayChecklistController {

    private final FirstDayChecklistService checklistService;

    @PostMapping
    @Operation(deprecated = true, summary = "Добавить пункт чек-листа", description = "Запись отключена после серверного переноса. Используйте API обычных задач и папок. После проверки доступа возвращает 410 FIRST_DAY_CHECKLIST_RETIRED, включая архивные проекты.",
            responses = {
                    @ApiResponse(responseCode = "410", description = "FIRST_DAY_CHECKLIST_RETIRED: Запись отключена; используйте API задач и папок", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ChecklistItemResponse> create(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @RequestBody(required = false) CreateChecklistItemRequest request,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.create(projectId, request, user.getId()));
    }

    @GetMapping
    @Operation(deprecated = true, summary = "Получить чек-лист первого дня", description = "Архивный снимок на момент серверного переноса. Изменения и удаление задач или вещей не меняют архив. GET ничего не переносит. Сортировка createdAt ASC, id ASC; page от 0, size от 1 до 100, по умолчанию 0 и 20. Актуальный план доступен через API задач и папок.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница пунктов чек-листа"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректная пагинация", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<ChecklistItemResponse>> findAll(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
                                                                      @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.findAll(projectId, pagination, user.getId()));
    }

    @PatchMapping("/{itemId}")
    @Operation(deprecated = true, summary = "Изменить название или выполнение пункта", description = "Запись отключена после серверного переноса. Используйте API обычных задач и папок. После проверки доступа возвращает 410 FIRST_DAY_CHECKLIST_RETIRED, включая архивные проекты.",
            responses = {
                    @ApiResponse(responseCode = "410", description = "FIRST_DAY_CHECKLIST_RETIRED: Запись отключена; используйте API задач и папок", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ChecklistItemResponse> update(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор пункта чек-листа") @PathVariable UUID itemId,
                                                       @RequestBody(required = false) UpdateChecklistItemRequest request, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.update(projectId, itemId, request, user.getId()));
    }

    @PutMapping("/{checklistItemId}/item")
    @Operation(deprecated = true, summary = "Привязать вещь к пункту или заменить связь", description = "Запись отключена после серверного переноса. Используйте API обычных задач и папок. После проверки доступа возвращает 410 FIRST_DAY_CHECKLIST_RETIRED, включая архивные проекты.",
            responses = {
                    @ApiResponse(responseCode = "410", description = "FIRST_DAY_CHECKLIST_RETIRED: Запись отключена; используйте API задач и папок", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ChecklistItemResponse> link(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор пункта чек-листа") @PathVariable UUID checklistItemId,
            @RequestBody(required = false) LinkChecklistItemRequest request, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.link(projectId, checklistItemId, request == null ? null : request.getItemId(), user.getId()));
    }

    @DeleteMapping("/{checklistItemId}/item")
    @Operation(deprecated = true, summary = "Снять связь с вещью", description = "Запись отключена после серверного переноса. Используйте API обычных задач и папок. После проверки доступа возвращает 410 FIRST_DAY_CHECKLIST_RETIRED, включая архивные проекты.",
            responses = {
                    @ApiResponse(responseCode = "410", description = "FIRST_DAY_CHECKLIST_RETIRED: Запись отключена; используйте API задач и папок", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<ChecklistItemResponse> unlink(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор пункта чек-листа") @PathVariable UUID checklistItemId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.unlink(projectId, checklistItemId, user.getId()));
    }

    @DeleteMapping("/{itemId}")
    @Operation(deprecated = true, summary = "Удалить пункт чек-листа", description = "Запись отключена после серверного переноса. Используйте API обычных задач и папок. После проверки доступа возвращает 410 FIRST_DAY_CHECKLIST_RETIRED, включая архивные проекты.",
            responses = {
                    @ApiResponse(responseCode = "410", description = "FIRST_DAY_CHECKLIST_RETIRED: Запись отключена; используйте API задач и папок", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> delete(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор пункта чек-листа") @PathVariable UUID itemId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        checklistService.delete(projectId, itemId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
