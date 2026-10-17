package ru.example.prodbackend.checklist.controller;

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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.checklist.dto.request.CreateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.UpdateChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.request.LinkChecklistItemRequest;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;
import ru.example.prodbackend.checklist.service.FirstDayChecklistService;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/first-day-checklist", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Чек-лист первого дня")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный UUID или JSON", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует или недействителен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; CHECKLIST_ITEM_NOT_FOUND: пункта нет в этом проекте; NOT_FOUND: вещь отсутствует или принадлежит другому проекту", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: вещь уже связана с другим пунктом", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class FirstDayChecklistController {

    private final FirstDayChecklistService checklistService;

    @PostMapping
    @Operation(summary = "Добавить пункт чек-листа", description = "Необязательный itemId связывает вещь этого проекта и включает firstDay. Название пункта независимо от названия вещи.", responses = {
            @ApiResponse(responseCode = "201", description = "Невыполненный пункт создан", content = @Content(schema = @Schema(implementation = ChecklistItemResponse.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistItemResponse> create(@PathVariable UUID projectId, @Valid @RequestBody CreateChecklistItemRequest request,
                                                       @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checklistService.create(projectId, request, user.getId()));
    }

    @GetMapping
    @Operation(summary = "Получить чек-лист первого дня", description = "Сортировка createdAt ASC, id ASC. page от 0, size от 1 до 100; по умолчанию 0 и 20. Название вещи, коробка и комната актуальны; без связи поля itemId, itemName, boxId, boxNumber, roomId, roomName равны null.", responses = {
            @ApiResponse(responseCode = "200", description = "Страница пунктов чек-листа"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: некорректная пагинация", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<ChecklistItemResponse>> findAll(@PathVariable UUID projectId, @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
                                                                      @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.findAll(projectId, pagination, user.getId()));
    }

    @PatchMapping("/{itemId}")
    @Operation(summary = "Изменить название или выполнение пункта", description = "Отсутствующие и null-поля сохраняются; пустой объект допустим. Повтор текущего completed не меняет время выполнения и updatedAt.", responses = {
            @ApiResponse(responseCode = "200", description = "Актуальный пункт чек-листа", content = @Content(schema = @Schema(implementation = ChecklistItemResponse.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistItemResponse> update(@PathVariable UUID projectId, @PathVariable UUID itemId,
                                                       @Valid @RequestBody UpdateChecklistItemRequest request, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.update(projectId, itemId, request, user.getId()));
    }

    @PutMapping("/{checklistItemId}/item")
    @Operation(summary = "Привязать вещь к пункту или заменить связь", description = "Вещь должна принадлежать этому проекту. Привязка включает firstDay=true. Повтор той же связи успешен; новая вещь заменяет старую. firstDay прежней вещи сохраняется.", responses = {
            @ApiResponse(responseCode = "200", description = "Пункт с актуальной связью", content = @Content(schema = @Schema(implementation = ChecklistItemResponse.class))),
            @ApiResponse(responseCode = "422", description = "itemId обязателен", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ChecklistItemResponse> link(@PathVariable UUID projectId, @PathVariable UUID checklistItemId,
            @Valid @RequestBody LinkChecklistItemRequest request, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.link(projectId, checklistItemId, request.getItemId(), user.getId()));
    }

    @DeleteMapping("/{checklistItemId}/item")
    @Operation(summary = "Снять связь с вещью", description = "Сохраняет пункт, название, выполнение и firstDay вещи. Повтор успешен. Возвращает пункт с null в полях связи.", responses = {
            @ApiResponse(responseCode = "200", description = "Самостоятельный пункт чек-листа", content = @Content(schema = @Schema(implementation = ChecklistItemResponse.class)))
    })
    public ResponseEntity<ChecklistItemResponse> unlink(@PathVariable UUID projectId, @PathVariable UUID checklistItemId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(checklistService.unlink(projectId, checklistItemId, user.getId()));
    }

    @DeleteMapping("/{itemId}")
    @Operation(summary = "Удалить пункт чек-листа", description = "Вещь и её firstDay сохраняются. Подтверждение выполняет интерфейс. Повторное удаление возвращает 404.", responses = {
            @ApiResponse(responseCode = "204", description = "Пункт удалён", content = @Content)
    })
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID itemId, @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        checklistService.delete(projectId, itemId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
