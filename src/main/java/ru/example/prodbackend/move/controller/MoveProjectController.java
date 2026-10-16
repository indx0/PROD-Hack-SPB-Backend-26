package ru.example.prodbackend.move.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.dto.request.UpdateMoveProjectRequest;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequestMapping(value = "/api/move-projects", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = "Проекты переезда")
public class MoveProjectController {

    private final MoveProjectService moveProjectService;

    @PostMapping
    @Operation(summary = "Создать проект переезда",
            description = "Создаёт проект, 11 задач из локальных шаблонов и 4 пункта чек-листа первого дня одной транзакцией. Сроки задач вычисляются относительно даты переезда в UTC. Адреса необязательны; дата — ISO 8601 с часовым поясом. PROJECT_CREATED записывается один раз. Последующая смена даты проекта не меняет сроки задач.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Проект создан", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: тело отсутствует, некорректный JSON или формат даты", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: текущий пользователь больше не существует", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: название пустое или длиннее 255 символов, дата отсутствует либо адрес длиннее 1000 символов; подробности в fieldErrors", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> createMoveProject(
            @Valid @RequestBody CreateMoveProjectRequest request,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveProjectService.createMoveProject(request, user));
    }

    @GetMapping
    @Operation(summary = "Получить список своих переездов",
            description = "Возвращает только проекты текущего пользователя, новые первыми (createdAt DESC, id DESC). Страницы начинаются с 0; по умолчанию page=0, size=20. Пустая или выходящая за пределы списка страница возвращает пустой content.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница проектов получена"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: page должен быть целым числом не меньше 0, size — целым числом от 1 до 100; подробности в fieldErrors", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<MoveProjectResponse>> getMoveProjects(
            @Valid @ModelAttribute @ParameterObject PaginationRequest request,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.getMoveProjects(request, user));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Получить свой проект переезда",
            description = "Возвращает проект только его владельцу. Для чужого и несуществующего проекта ответ одинаковый: 404.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проект получен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: projectId не является UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> getMoveProject(
            @PathVariable("projectId") UUID projectId,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.getMoveProject(projectId, user));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Изменить свой проект переезда",
            description = "Отсутствующие и null-поля не изменяются. Пустой адрес удаляется. Пустой объект допустим. Изменение даты проекта не переносит сроки задач.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проект обновлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: тело отсутствует, некорректный JSON или дата; INVALID_REQUEST_PARAMETER: projectId не является UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: передано пустое название, название длиннее 255 символов или адрес длиннее 1000 символов; подробности в fieldErrors", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> updateMoveProject(
            @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody UpdateMoveProjectRequest request,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.updateMoveProject(projectId, request, user));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Удалить свой проект переезда",
            description = "Безвозвратно удаляет проект, комнаты, коробки, вещи, задачи, чек-лист и события одной транзакцией. Перед вызовом интерфейс должен запросить подтверждение. Повторное удаление возвращает 404.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Проект и его содержимое удалены; тело ответа отсутствует", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: projectId не является UUID", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект не существует или принадлежит другому пользователю", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteMoveProject(
            @PathVariable("projectId") UUID projectId,
            @AuthenticationPrincipal UserEntity user) {
        moveProjectService.deleteMoveProject(projectId, user);
        return ResponseEntity.noContent().build();
    }
}
