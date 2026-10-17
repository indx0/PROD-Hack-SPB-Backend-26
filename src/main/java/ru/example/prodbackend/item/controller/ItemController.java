package ru.example.prodbackend.item.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.item.dto.CreateItemRequest;
import ru.example.prodbackend.item.dto.CreateItemsRequest;
import ru.example.prodbackend.item.dto.ItemBoxSuggestionResponse;
import ru.example.prodbackend.item.dto.ItemResponse;
import ru.example.prodbackend.item.dto.MoveItemRequest;
import ru.example.prodbackend.item.dto.UpdateItemRequest;
import ru.example.prodbackend.item.service.ItemService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping("/api/move-projects/{projectId}")
@RequiredArgsConstructor
@Tag(name = SwaggerTags.ITEMS)
@SecurityRequirement(name = "jwtAuth")
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "Получить карточку вещи",
            description = "Возвращает вещь доступного участнику проекта, включая отметки упаковки и распаковки.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Карточка вещи",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/boxes/{boxId}/items/{itemId}")
    public ResponseEntity<ItemResponse> findById(@Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId, @Parameter(description = "Идентификатор вещи") @PathVariable UUID itemId) {
        return ResponseEntity.ok(itemService.findById(user.getId(), projectId, boxId, itemId));
    }

    @Operation(summary = "Получить содержимое коробки",
            description = """
                    Возвращает страницу вещей коробки доступного участнику проекта.
                    page начинается с 0, size — от 1 до 100, по умолчанию 20.
                    Сортировка фиксирована: createdAt ASC, id ASC; параметр sort игнорируется.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница вещей"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации запроса",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/boxes/{boxId}/items")
    public ResponseEntity<PageResponse<ItemResponse>> findAll(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination
    ) {
        return ResponseEntity.ok(itemService.findAll(user.getId(), projectId, boxId, pagination));
    }

    @Operation(summary = "Добавить вещь в коробку",
            description = """
                    Название обязательно, до 255 символов. Пробелы по краям удаляются.
                    quantity — положительное целое, по умолчанию 1; firstDay — по умолчанию false.
                    Явный null для quantity или firstDay запрещён (422).
                    firstDay не создаёт и не удаляет пункты чек-листа.
                    Новая вещь не помечена потерянной. Одинаковые названия допустимы.
                    flags принимает fragile, first_day, carry_personally без повторов; null запрещён.
                    first_day и firstDay обозначают один признак; противоречие в запросе — 422.
                    Без flags и firstDay метки отсутствуют.
                    Отметки упаковки и распаковки отсутствуют; время задаётся сервером через PATCH.
                    Добавление не изменяет статус коробки и contentsDeferred.
                    Добавление разрешено только в PACKING; после сборки возвращается 409.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Вещь добавлена",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Недопустимый статус коробки или изменившееся расположение вещи; для изменений — переезд в архиве, сначала восстановите его",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации запроса",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/boxes/{boxId}/items")
    public ResponseEntity<ItemResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Valid @RequestBody CreateItemRequest request
    ) {
        ItemResponse response = itemService.create(user.getId(), projectId, boxId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Добавить несколько вещей",
            description = """
                    Принимает ровно один список: names (названия) или items (объекты вещей).
                    В каждом списке от 1 до 100 записей. Название обязательно, до 255 символов.
                    В items доступны quantity (по умолчанию 1) и firstDay (по умолчанию false).
                    Для names используются quantity=1 и firstDay=false.
                    Все новые вещи создаются без отметок упаковки и распаковки.
                    Добавление разрешено только в PACKING; после сборки возвращается 409.
                    Весь список сохраняется в одной транзакции; при ошибке ничего не добавляется.
                    Пробелы по краям удаляются, повторяющиеся названия сохраняются отдельными вещами.
                    Результат соответствует порядку входного списка.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Список добавленных вещей"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Недопустимый статус коробки или изменившееся расположение вещи; для изменений — переезд в архиве, сначала восстановите его",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации запроса",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/boxes/{boxId}/items/batch")
    public ResponseEntity<List<ItemResponse>> createBatch(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Valid @RequestBody CreateItemsRequest request
    ) {
        return ResponseEntity.status(201)
                .body(itemService.createBatch(user.getId(), projectId, boxId, request));
    }

    @Operation(summary = "Изменить вещь и отметки потери, упаковки и распаковки",
            description = """
                    Изменяет name, lost, quantity, firstDay, flags, packed и unpacked в любом сочетании.
                    flags заменяет все метки; [] снимает все, отсутствие сохраняет их.
                    firstDay меняет только признак первого дня. При противоречии с flags.first_day — 422.
                    Снятие first_day через flags также отвязывает вещь от чек-листа, сохраняя пункт.
                    packed изменяется только в коробке PACKING, unpacked — только в DELIVERED; иначе 409.
                    true устанавливает серверное время, false очищает. Отметка относится ко всему quantity.
                    Повтор уже установленного состояния успешен в любом статусе и сохраняет время.
                    При конфликте ни одно поле запроса не изменяется. Статус коробки автоматически не меняется.
                    quantity — положительное целое. lost=true помечает вещь потерянной.
                    Изменение quantity разрешено только в PACKING и сбрасывает packedAt, даже при packed=true в том же запросе.
                    Повтор того же quantity не сбрасывает отметку и допустим в любом статусе.
                    name, lost и firstDay доступны в любом статусе и не сбрасывают отметки.
                    firstDay=false снимает связь с пунктом первого дня, сохраняя сам пункт, его название и выполнение.
                    firstDay не создаёт и не удаляет пункты чек-листа.
                    Отсутствующие поля сохраняют значения. Неизвестные поля игнорируются.
                    Запрос без поддерживаемых полей запрещён (422), явный null запрещён (400).
                    Для переноса вещи используется отдельный PUT /boxes/{sourceBoxId}/items/{itemId}/box.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Вещь обновлена",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Недопустимый статус коробки для изменения отметки; для изменений — переезд в архиве, сначала восстановите его",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации запроса",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PatchMapping("/boxes/{boxId}/items/{itemId}")
    public ResponseEntity<ItemResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Parameter(description = "Идентификатор вещи") @PathVariable UUID itemId,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        return ResponseEntity.ok(itemService.update(user.getId(), projectId, boxId, itemId, request));
    }

    @Operation(summary = "Перенести вещь в другую коробку",
            description = """
                    Обе коробки должны принадлежать этому проекту и находиться в PACKING.
                    Переносится вся запись: UUID, название, quantity, lost, firstDay, отметки и связь с чек-листом сохраняются.
                    Статусы коробок автоматически не меняются. Ответ содержит новое расположение.
                    Если вещь уже в целевой коробке, возвращает успех без изменения времени в любом статусе.
                    Если вещь находится в третьей коробке, возвращает 409. При отказе данные сохраняются.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Вещь с актуальным расположением",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Недопустимый статус коробки или изменившееся расположение вещи; для изменений — переезд в архиве, сначала восстановите его",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации запроса",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping("/boxes/{sourceBoxId}/items/{itemId}/box")
    public ResponseEntity<ItemResponse> move(@Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор исходной коробки") @PathVariable UUID sourceBoxId, @Parameter(description = "Идентификатор вещи") @PathVariable UUID itemId,
            @Valid @RequestBody MoveItemRequest request) {
        return ResponseEntity.ok(itemService.move(user.getId(), projectId, sourceBoxId, itemId, request));
    }

    @Operation(summary = "Удалить вещь",
            description = "Удаляет вещь только в коробке PACKING, после проверки участия в проекте. Иначе 409. Подтверждение показывает клиент.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Вещь удалена", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Недопустимый статус коробки или изменившееся расположение вещи; для изменений — переезд в архиве, сначала восстановите его",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @DeleteMapping("/boxes/{boxId}/items/{itemId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Parameter(description = "Идентификатор вещи") @PathVariable UUID itemId
    ) {
        itemService.delete(user.getId(), projectId, boxId, itemId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Найти вещи в проекте",
            description = """
                    q — непустая подстрока названия до 255 символов, регистр не учитывается.
                    Пустой q отклоняется встроенной проверкой параметров Spring MVC с HTTP 400.
                    Символы %, _ и ! ищутся буквально. lost позволяет фильтровать отметку потери.
                    Ответ содержит номер коробки, комнату и статус. Порядок: lower(name) ASC, id ASC;
                    параметр sort игнорируется. Отсутствующий или чужой проект возвращает пустую страницу.
                    page начинается с 0, size — от 1 до 100, по умолчанию 20.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница результатов поиска"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Некорректная пагинация: page >= 0, size от 1 до 100",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/items")
    public ResponseEntity<PageResponse<ItemResponse>> search(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Подстрока названия вещи: от 1 до 255 символов, без учёта регистра") @RequestParam @NotBlank @Size(max = 255) String q,
            @Parameter(description = "Фильтр потери: true — потерянные, false — остальные; отсутствие — все вещи") @RequestParam(required = false) Boolean lost,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination
    ) {
        return ResponseEntity.ok(itemService.search(user.getId(), projectId, q, lost, pagination));
    }

    @Operation(summary = "Получить подсказки для поиска вещи",
            description = """
                    Возвращает нераспакованные коробки того же проекта с совпадением названия в содержимом
                    либо с той же комнатой назначения. Сначала совпадение содержимого, затем комнаты,
                    затем номер коробки и UUID. Поля contentsMatch и sameRoom объясняют подсказку.
                    Исходная коробка также может попасть в результат. Это подсказки, а не подтверждение наличия вещи.
                    limit: от 1 до 100, по умолчанию 10. Некорректный limit отклоняется встроенной проверкой параметров Spring MVC с HTTP 400. Состояние вещи не изменяется.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список коробок для поиска"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Коробка или вещь не найдена либо недоступна пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/items/{itemId}/suggestions")
    public ResponseEntity<List<ItemBoxSuggestionResponse>> suggestions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Parameter(description = "Идентификатор вещи") @PathVariable UUID itemId,
            @Parameter(description = "Максимальное количество подсказок: от 1 до 100, по умолчанию 10") @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(itemService.suggestions(user.getId(), projectId, itemId, limit));
    }
}
