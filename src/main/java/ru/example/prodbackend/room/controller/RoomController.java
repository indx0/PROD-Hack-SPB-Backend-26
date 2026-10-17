package ru.example.prodbackend.room.controller;

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
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.room.dto.request.CreateRoomRequest;
import ru.example.prodbackend.room.dto.request.UpdateRoomRequest;
import ru.example.prodbackend.room.dto.response.RoomResponse;
import ru.example.prodbackend.room.service.RoomService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/move-projects/{projectId}/rooms", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Комнаты", description = "Комнаты назначения в проекте переезда")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Некорректный UUID или тело JSON", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RoomController {

    private final RoomService roomService;

    @PostMapping
    @Operation(summary = "Создать комнату",
            description = """
                    Создаёт комнату в своём проекте. Пробелы по краям названия удаляются; название уникально с учётом регистра.
                    iconKey — необязательный ключ значка клиента, непустая строка до 64 символов.
                    Отсутствие или null означает стандартное оформление; неизвестный клиенту ключ требует стандартного значка.
                    Сервер хранит ключ без определения значка по названию комнаты.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Комната создана", content = @Content(schema = @Schema(implementation = RoomResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: название уже занято в этом проекте", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов; iconKey пустой, пробельный или длиннее 64 символов", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<RoomResponse> createRoom(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody CreateRoomRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roomService.createRoom(projectId, request, user.getId()));
    }

    @GetMapping
    @Operation(summary = "Получить комнаты проекта",
            description = "Возвращает комнаты своего проекта с сортировкой name ASC, id ASC. По умолчанию page=0, size=20. Пустая страница возвращается с кодом 200.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница комнат получена"),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: page должен быть целым числом >= 0, size — целым числом от 1 до 100", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<RoomResponse>> getRooms(
            @PathVariable("projectId") UUID projectId,
            @Valid @ModelAttribute @ParameterObject PaginationRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(roomService.getRooms(projectId, request, user.getId()));
    }

    @PatchMapping("/{roomId}")
    @Operation(summary = "Изменить комнату",
            description = """
                    Изменяет название и выбранный значок комнаты своего проекта. Пустой объект допустим.
                    Отсутствующее name или name=null сохраняет название; переименование не меняет значок.
                    Отсутствующий iconKey сохраняет выбор; строка меняет его; iconKey=null очищает явный выбор.
                    Пустая или пробельная строка и ключ длиннее 64 символов запрещены.
                    Следующее чтение коробок, вещей, поиска, подсказок, чек-листа и сводки возвращает актуальный roomIconKey.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Комната обновлена", content = @Content(schema = @Schema(implementation = RoomResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; ROOM_NOT_FOUND: комнаты нет в этом проекте", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: название уже занято другой комнатой проекта", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов; iconKey пустой, пробельный или длиннее 64 символов", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<RoomResponse> updateRoom(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("roomId") UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(roomService.updateRoom(projectId, roomId, request, user.getId()));
    }

    @DeleteMapping("/{roomId}")
    @Operation(summary = "Удалить пустую комнату",
            description = "Удаляет комнату без коробок. Подтверждение выполняется интерфейсом. Повторное удаление возвращает 404.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Комната удалена, тело ответа отсутствует", content = @Content),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; ROOM_NOT_FOUND: комнаты нет в этом проекте", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: в комнате есть коробки", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteRoom(
            @PathVariable("projectId") UUID projectId,
            @PathVariable("roomId") UUID roomId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        roomService.deleteRoom(projectId, roomId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
