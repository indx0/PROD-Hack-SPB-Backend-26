package ru.example.prodbackend.move.controller;

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
import ru.example.prodbackend.common.dto.PageResponse;
import ru.example.prodbackend.common.dto.PaginationRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.move.dto.request.CreateMoveProjectRequest;
import ru.example.prodbackend.move.dto.request.RescheduleMoveProjectRequest;
import ru.example.prodbackend.move.dto.request.UpdateMoveProjectRequest;
import ru.example.prodbackend.move.dto.response.MoveProjectResponse;
import ru.example.prodbackend.move.dto.response.RescheduleMoveProjectResponse;
import ru.example.prodbackend.move.service.MoveProjectService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping(value = "/api/move-projects", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = "jwtAuth")
@Tag(name = SwaggerTags.PROJECTS)
public class MoveProjectController {

    private final MoveProjectService moveProjectService;

    @PostMapping
    @Operation(summary = "Создать проект переезда",
            description = "Создаёт проект и выбранное наполнение одной транзакцией. includeFirstDayChecklist=false оставляет чек-лист пустым; true или отсутствие поля добавляет четыре стандартных пункта; null — 422. Чтение проекта или чек-листа не добавляет пункты повторно. Задачи добровольны: без taskSetIds или с [] задачи не добавляются. taskSetIds содержит до 100 UUID собственных активных наборов; повторы объединяются. Наборы применяются целиком, недоступный набор откатывает создание проекта, участников, задач и чек-листа. Общие шаблоны применяются отдельно через from-template. Необязательный participants содержит до 100 записей с userId или email существующих аккаунтов; они получают равные права сразу, без подтверждения. Повторы пользователей не создают дубли, создатель уже имеет доступ. Если хотя бы один пользователь не найден, создание целиком откатывается. Сроки задач вычисляются относительно даты переезда в UTC. Адреса необязательны; дата — ISO 8601 с часовым поясом. PROJECT_CREATED записывается один раз. Последующая смена даты проекта не меняет сроки задач.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Проект создан", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Текущий пользователь или добавляемый участник не найден; TASK_SET_NOT_FOUND: Выбранный собственный набор недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Название пустое или длиннее 255 символов, дата отсутствует либо адрес длиннее 1000 символов; подробности в fieldErrors", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> createMoveProject(
            @Valid @RequestBody CreateMoveProjectRequest request,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(moveProjectService.createMoveProject(request, user));
    }

    @GetMapping
    @Operation(summary = "Получить список своих переездов",
            description = "Возвращает созданные пользователем и доступные ему совместные проекты, новые первыми (createdAt DESC, id DESC). archived=true — архивные, false — активные; без параметра возвращаются все. Фильтрация выполняется до пагинации. Страницы начинаются с 0; по умолчанию page=0, size=20. Пустая или выходящая за пределы списка страница возвращает пустой content.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Страница проектов получена"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: page должен быть целым числом не меньше 0, size — целым числом от 1 до 100; подробности в fieldErrors", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<PageResponse<MoveProjectResponse>> getMoveProjects(
            @Valid @ModelAttribute @ParameterObject PaginationRequest request,
            @Parameter(description = "Архивные/активные проекты; без параметра — все") @RequestParam(required = false) Boolean archived,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.getMoveProjects(request, archived, user));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "Получить свой проект переезда",
            description = "Возвращает проект создателю и добавленным участникам. Для чужого и несуществующего проекта ответ одинаковый: 404.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проект получен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не существует или недоступен текущему пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> getMoveProject(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable("projectId") UUID projectId,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.getMoveProject(projectId, user));
    }

    @PatchMapping("/{projectId}")
    @Operation(summary = "Изменить свой проект переезда",
            description = "Отсутствующие и null-поля не изменяются. Пустой адрес удаляется. Пустой объект допустим. Изменение даты проекта не переносит сроки задач.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проект обновлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не существует или недоступен текущему пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Передано пустое название, название длиннее 255 символов или адрес длиннее 1000 символов; подробности в fieldErrors", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> updateMoveProject(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable("projectId") UUID projectId,
            @Valid @RequestBody UpdateMoveProjectRequest request,
            @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.updateMoveProject(projectId, request, user));
    }

    @PostMapping("/{projectId}/reschedule")
    @Operation(summary = "Перенести дату переезда и выбранные относительные сроки",
            description = "Доступно создателю и участникам активного проекта. taskIds обязателен: [] меняет только дату проекта. Все выбранные задачи должны принадлежать проекту, быть невыполненными и иметь RELATIVE. Новая dueDate = календарная дата moveDate в UTC + сохранённый offsetDays. Невыбранные задачи сохраняются. Весь запрос атомарен. Повтор не сдвигает сроки второй раз и не меняет временные отметки при совпадении значений. Задачи проверяются даже при совпадении даты проекта. Порядок ответа соответствует первым вхождениям taskIds. Обычный PATCH проекта сроки не пересчитывает; клиентский дополнительный пересчёт после reschedule не нужен.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Актуальный проект и выбранные задачи", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RescheduleMoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля; INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект недоступен; TASK_NOT_FOUND: Выбранной задачи нет в проекте", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Проект в архиве либо выбрана выполненная или FIXED-задача", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Обязательные поля отсутствуют, taskIds содержит null либо срок выходит за диапазон дат", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<RescheduleMoveProjectResponse> rescheduleMoveProject(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId,
            @Valid @RequestBody RescheduleMoveProjectRequest request, @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.rescheduleMoveProject(projectId, request, user));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "Удалить свой проект переезда",
            description = "Безвозвратно удаляет активный проект, комнаты, коробки, вещи, задачи, чек-лист и события одной транзакцией. Архивный проект сначала нужно восстановить. Перед вызовом интерфейс должен запросить подтверждение. Повторное удаление возвращает 404.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Проект и его содержимое удалены; тело ответа отсутствует", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не существует или недоступен текущему пользователю", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: Переезд в архиве; сначала восстановите его", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<Void> deleteMoveProject(
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable("projectId") UUID projectId,
            @AuthenticationPrincipal UserEntity user) {
        moveProjectService.deleteMoveProject(projectId, user);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{projectId}/archive")
    @Operation(summary = "Архивировать переезд",
            description = "Сохраняет archivedAt и запрещает изменения проекта и всего содержимого, включая удаление. Чтение, поиск, статистика и журнал доступны. Можно архивировать на любом этапе. Повтор не меняет время и не создаёт событие. PROJECT_ARCHIVED записывается одной транзакцией.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Архивный проект или успешный повтор", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> archiveMoveProject(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.archiveMoveProject(projectId, user));
    }

    @DeleteMapping("/{projectId}/archive")
    @Operation(summary = "Восстановить переезд из архива",
            description = "Очищает archivedAt и снова разрешает изменения. Проект и его содержимое сохраняются. Для уже активного проекта успешный ответ без изменения времени и нового события. PROJECT_RESTORED записывается одной транзакцией.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Активный проект или успешный повтор", content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveProjectResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный формат параметра запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект не найден или недоступен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    public ResponseEntity<MoveProjectResponse> restoreMoveProject(@Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(moveProjectService.restoreMoveProject(projectId, user));
    }
}
