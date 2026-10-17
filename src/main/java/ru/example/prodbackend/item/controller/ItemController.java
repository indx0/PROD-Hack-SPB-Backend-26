package ru.example.prodbackend.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.dto.ItemBoxSuggestionResponse;
import ru.example.prodbackend.item.dto.ItemResponse;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/move-projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = "Items", description = "Содержимое коробок, поиск вещей и подсказки поиска")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный JSON, UUID или параметры запроса"),
        @ApiResponse(responseCode = "401", description = "Требуется действующий access токен",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Коробка или вещь не найдена либо недоступна пользователю",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Ошибка валидации запроса",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "Получение содержимого коробки",
            description = """
                    Возвращает страницу вещей коробки владельца проекта.
                    page начинается с 0, size — от 1 до 100, по умолчанию 20.
                    Сортировка фиксирована: createdAt ASC, id ASC; параметр sort игнорируется.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Страница вещей"))
    @GetMapping("/boxes/{boxId}/items")
    public ResponseEntity<PageResponse<ItemResponse>> findAll(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination
    ) {
        return ResponseEntity.ok(itemService.findAll(user.getId(), projectId, boxId, pagination));
    }

    @Operation(summary = "Добавление вещи в коробку",
            description = """
                    Название обязательно, до 255 символов. Пробелы по краям удаляются.
                    Новая вещь не помечена потерянной. Одинаковые названия допустимы.
                    Необязательный flags: fragile, first_day, carry_personally без повторов.
                    Без flags сохраняется пустой набор; null недопустим.
                    Добавление не изменяет статус коробки и contentsDeferred.
                    """,
            responses = @ApiResponse(responseCode = "201", description = "Вещь добавлена",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))))
    @PostMapping("/boxes/{boxId}/items")
    public ResponseEntity<ItemResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @Valid @RequestBody CreateItemRequest request
    ) {
        ItemResponse response = itemService.create(user.getId(), projectId, boxId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Быстрый ввод нескольких вещей",
            description = """
                    Принимает names: от 1 до 100 непустых названий, каждое до 255 символов.
                    Весь список сохраняется в одной транзакции; при ошибке ничего не добавляется.
                    Пробелы по краям удаляются, повторяющиеся названия сохраняются отдельными вещами.
                    Результат соответствует порядку входного списка.
                    """,
            responses = @ApiResponse(responseCode = "201", description = "Список добавленных вещей"))
    @PostMapping("/boxes/{boxId}/items/batch")
    public ResponseEntity<List<ItemResponse>> createBatch(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @Valid @RequestBody CreateItemsRequest request
    ) {
        return ResponseEntity.status(201)
                .body(itemService.createBatch(user.getId(), projectId, boxId, request));
    }

    @Operation(summary = "Изменение вещи, меток и отметки потери",
            description = """
                    flags заменяет весь набор меток; [] снимает все, отсутствие сохраняет текущие.
                    Допустимы fragile, first_day, carry_personally без повторов; null запрещён.
                    Изменяет name, flags и/или lost. lost=true помечает вещь потерянной, false снимает отметку.
                    Отсутствующие поля сохраняют значения. Неизвестные поля игнорируются.
                    Запрос без поддерживаемых полей и явный null запрещены.
                    Перенос вещи в другую коробку не поддерживается.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Вещь обновлена",
                    content = @Content(schema = @Schema(implementation = ItemResponse.class))))
    @PatchMapping("/boxes/{boxId}/items/{itemId}")
    public ResponseEntity<ItemResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return ResponseEntity.ok(itemService.update(user.getId(), projectId, boxId, itemId, request));
    }

    @Operation(summary = "Удаление вещи",
            description = "Удаляет отдельную вещь после проверки владельца. Подтверждение показывает клиент.",
            responses = @ApiResponse(responseCode = "204", description = "Вещь удалена", content = @Content))
    @DeleteMapping("/boxes/{boxId}/items/{itemId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @PathVariable UUID itemId
    ) {
        itemService.delete(user.getId(), projectId, boxId, itemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Поиск вещей по проекту",
            description = """
                    q — непустая подстрока названия до 255 символов, регистр не учитывается.
                    Символы %, _ и ! ищутся буквально. lost позволяет фильтровать отметку потери.
                    Ответ содержит номер коробки, комнату и статус. Порядок: lower(name) ASC, id ASC;
                    параметр sort игнорируется. Отсутствующий или чужой проект возвращает пустую страницу.
                    page начинается с 0, size — от 1 до 100, по умолчанию 20.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Страница результатов поиска"))
    @GetMapping("/items")
    public ResponseEntity<PageResponse<ItemResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @RequestParam @NotBlank @Size(max = 255) String q,
            @RequestParam(required = false) Boolean lost,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination
    ) {
        return ResponseEntity.ok(itemService.search(user.getId(), projectId, q, lost, pagination));
    }

    @Operation(summary = "Подсказки, в каких коробках искать вещь",
            description = """
                    Возвращает нераспакованные коробки того же проекта с совпадением названия в содержимом
                    либо с той же комнатой назначения. Сначала совпадение содержимого, затем комнаты,
                    затем номер коробки и UUID. Поля contentsMatch и sameRoom объясняют подсказку.
                    Исходная коробка также может попасть в результат. Это подсказки, а не подтверждение наличия вещи.
                    limit: от 1 до 100, по умолчанию 10. Состояние вещи не изменяется.
                    """,
            responses = @ApiResponse(responseCode = "200", description = "Список коробок для поиска"))
    @GetMapping("/items/{itemId}/suggestions")
    public ResponseEntity<List<ItemBoxSuggestionResponse>> suggestions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID itemId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(itemService.suggestions(user.getId(), projectId, itemId, limit));
    }
}
