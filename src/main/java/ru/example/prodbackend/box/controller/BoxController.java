package ru.example.prodbackend.box.controller;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import ru.example.prodbackend.box.dto.BoxFilter;
import ru.example.prodbackend.box.dto.BoxResponse;
import ru.example.prodbackend.box.dto.BoxSummaryResponse;
import ru.example.prodbackend.box.dto.ChangeBoxStatusRequest;
import ru.example.prodbackend.box.dto.CreateBoxRequest;
import ru.example.prodbackend.box.dto.UpdateBoxRequest;
import ru.example.prodbackend.box.service.BoxService;
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.entity.UserEntity;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/projects/{projectId}/boxes", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Boxes", description = "Коробки проекта переезда")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный UUID, параметры запроса или тело JSON"),
        @ApiResponse(responseCode = "401", description = "Access токен отсутствует, некорректный или истек",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class BoxController {

    private final BoxService boxService;

    @Operation(
            summary = "Создание коробки",
            description = """
                    Создаёт коробку с положительным номером, уникальным внутри проекта,
                    комнатой назначения и начальным статусом PACKING.
                    contentsDeferred позволяет отложить заполнение содержимого.
                    Пока недоступно: в сервисе не реализована интеграция с Move, Room и Event.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Коробка создана; Location содержит её адрес",
                            content = @Content(schema = @Schema(implementation = BoxResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Ошибка валидации тела запроса",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "500", description = "Создание пока не реализовано: TODO интеграции с другими модулями")
            }
    )
    @PostMapping
    public ResponseEntity<BoxResponse> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateBoxRequest request
    ) {
        BoxResponse response = boxService.create(user.getId(), projectId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{boxId}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @Operation(
            summary = "Получение списка коробок",
            description = """
                    Возвращает страницу коробок проекта. Фильтры объединяются через AND.
                    Поиск по номеру использует точное совпадение; отсутствие результата означает пустую страницу.
                    page начинается с 0, size — от 1 до 100, по умолчанию page=0 и size=20.
                    Сортировка фиксирована: number ASC, id ASC. Пользовательская сортировка не поддерживается.
                    Пустая или выходящая за пределы списка страница возвращает пустой content.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница коробок проекта"),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: некорректные фильтры или параметры пагинации; page >= 0, size от 1 до 100, number > 0; подробности в fieldErrors", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @GetMapping
    public ResponseEntity<PageResponse<BoxResponse>> findAll(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @Valid @ModelAttribute @ParameterObject BoxFilter filter,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination) {
        return ResponseEntity.ok(boxService.findAll(user.getId(), projectId, filter, pagination));
    }

    @Operation(
            summary = "Получение карточки коробки",
            description = """
                    Возвращает коробку указанного проекта, принадлежащего текущему пользователю.
                    Используется также для открытия карточки после сканирования QR-кода.
                    Запрос не создаёт коробку и не изменяет её состояние. Список вещей загружается отдельно.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Карточка коробки",
                            content = @Content(schema = @Schema(implementation = BoxResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Коробка не найдена в проекте или недоступна пользователю")
            }
    )
    @GetMapping("/{boxId}")
    public ResponseEntity<BoxResponse> findById(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId
    ) {
        return ResponseEntity.ok(boxService.findById(user.getId(), projectId, boxId));
    }

    @Operation(
            summary = "Редактирование коробки",
            description = """
                    Изменяет номер и признак contentsDeferred. Отсутствующие поля сохраняют значения.
                    Пустой запрос, явный null и неизвестные поля запрещены.
                    Статус изменяется отдельным запросом.
                    Смена комнаты пока недоступна: требуется интеграция с Room.
                    Передача идентификатора текущей комнаты допустима.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Коробка обновлена",
                            content = @Content(schema = @Schema(implementation = BoxResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Коробка не найдена в проекте или недоступна пользователю"),
                    @ApiResponse(responseCode = "409", description = "Номер уже занят другой коробкой проекта"),
                    @ApiResponse(responseCode = "422", description = "Ошибка валидации"),
                    @ApiResponse(responseCode = "500", description = "Смена комнаты пока не реализована")
            }
    )
    @PatchMapping("/{boxId}")
    public ResponseEntity<BoxResponse> update(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @Valid @RequestBody UpdateBoxRequest request
    ) {
        return ResponseEntity.ok(boxService.update(user.getId(), projectId, boxId, request));
    }

    @Operation(
            summary = "Изменение статуса коробки",
            description = """
                    Разрешён следующий шаг: PACKING → PACKED → LOADED → DELIVERED → UNPACKED.
                    Повтор текущего статуса успешен и не изменяет updatedAt.
                    Пропуск этапа и возврат назад запрещены.
                    Запись события BOX_STATUS_CHANGED пока не реализована: TODO модуля Event.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Коробка с актуальным статусом",
                            content = @Content(schema = @Schema(implementation = BoxResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Коробка не найдена в проекте или недоступна пользователю"),
                    @ApiResponse(responseCode = "409", description = "Недопустимый переход статуса"),
                    @ApiResponse(responseCode = "422", description = "Статус обязателен",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @PatchMapping("/{boxId}/status")
    public ResponseEntity<BoxResponse> changeStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId,
            @Valid @RequestBody ChangeBoxStatusRequest request
    ) {
        return ResponseEntity.ok(
                boxService.changeStatus(user.getId(), projectId, boxId, request.getStatus())
        );
    }

    @Operation(
            summary = "Удаление коробки",
            description = """
                    Удаляет коробку вместе с содержимым. Подтверждение выполняется на клиенте.
                    Вещи и коробка удаляются в одной транзакции после проверки владельца проекта.
                    """,
            responses = {
                    @ApiResponse(responseCode = "204", description = "Коробка удалена", content = @Content),
                    @ApiResponse(responseCode = "404", description = "Коробка не найдена в проекте или недоступна пользователю")
            }
    )
    @DeleteMapping("/{boxId}")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @PathVariable UUID boxId
    ) {
        boxService.delete(user.getId(), projectId, boxId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Получение статистики коробок",
            description = """
                    Возвращает общее количество коробок, счётчики всех пяти статусов,
                    количество коробок с отложенным заполнением и прогресс распаковки по комнатам.
                    Включает только комнаты с коробками. Процент распаковки округляется до целого.
                    Для пустого проекта возвращаются нули и пустой список комнат.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Статистика коробок проекта",
                            content = @Content(schema = @Schema(implementation = BoxSummaryResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @GetMapping("/summary")
    public ResponseEntity<BoxSummaryResponse> summary(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(boxService.summary(user.getId(), projectId));
    }
}
