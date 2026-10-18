package ru.example.prodbackend.taskfolder.controller;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.common.dto.*;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.taskfolder.dto.*;
import ru.example.prodbackend.taskfolder.service.TaskFolderService;
import ru.example.prodbackend.user.entity.UserEntity;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/move-projects/{projectId}/task-folders", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = SwaggerTags.TASK_FOLDERS)
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: некорректный JSON; INVALID_REQUEST_PARAMETER: некорректный формат параметра", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: требуется действующий токен доступа", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: проект недоступен; TASK_FOLDER_NOT_FOUND: папка отсутствует или принадлежит другому проекту", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class TaskFolderController {
    private final TaskFolderService service;

    @GetMapping
    @Operation(summary = "Получить папки задач переезда", description = """
            Доступно любому участнику, включая чтение архивного переезда. Сортировка createdAt ASC, id ASC;
            переименование не меняет порядок. page >= 0, size от 1 до 100, по умолчанию 0 и 20.
            Возвращает content/page/size/totalElements/totalPages; пустая страница — 200.
            taskCount и completedTaskCount включают реальные задачи папки по всем трём этапам,
            каждую ровно один раз; для пустой папки 0/0. Шаблоны отдельно не учитываются.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Страница папок"),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: недопустимая пагинация", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<PageResponse<TaskFolderResponse>> findAll(
            @Parameter(description = "UUID переезда") @PathVariable UUID projectId,
            @Valid @ModelAttribute @ParameterObject PaginationRequest pagination,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findAll(projectId, pagination, user.getId()));
    }

    @GetMapping("/{folderId}")
    @Operation(summary = "Получить папку задач", description = "Доступно любому участнику, включая архивный переезд. Отсутствующая или чужая папка — 404.",
            responses = @ApiResponse(responseCode = "200", description = "Папка с актуальными счётчиками", content = @Content(schema = @Schema(implementation = TaskFolderResponse.class))))
    public ResponseEntity<TaskFolderResponse> findById(
            @Parameter(description = "UUID переезда") @PathVariable UUID projectId,
            @Parameter(description = "UUID папки") @PathVariable UUID folderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.findById(projectId, folderId, user.getId()));
    }

    @PostMapping
    @Operation(summary = "Создать папку задач", description = """
            Любой участник может создать пустую папку с обязательным клиентским UUID; одинаковые названия допустимы.
            Имя обрезается по краям до проверки 1..80 символов. Цвет: mint/cyan/coral/sand/lilac/forest.
            Первое создание — 201. Повтор UUID с теми же исходными данными — 200, если папка с тех пор
            не отличается от исходных имени и цвета. Другие данные, чужой UUID или изменённая папка — 409;
            повтор не перезаписывает правки. Архивный переезд запрещает создание, включая повторы.
            """, responses = {
            @ApiResponse(responseCode = "201", description = "Папка создана", content = @Content(schema = @Schema(implementation = TaskFolderResponse.class))),
            @ApiResponse(responseCode = "200", description = "Подтверждён повтор создания", content = @Content(schema = @Schema(implementation = TaskFolderResponse.class))),
            @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: UUID занят, папка изменена или переезд в архиве", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: обязательный UUID, имя 1..80 после обрезки или допустимый colorKey; fieldErrors указывает поле", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskFolderResponse> create(
            @Parameter(description = "UUID переезда") @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskFolderRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        var result = service.create(projectId, request, user.getId());
        return ResponseEntity.status(result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK).body(result.getFolder());
    }

    @PatchMapping("/{folderId}")
    @Operation(summary = "Изменить название или цвет папки", description = """
            Доступно любому участнику. Отсутствующее поле сохраняет значение; явный null запрещён.
            Пустой объект и повтор актуальных значений не меняют updatedAt. Имя обрезается по краям,
            затем проверяется длина 1..80; colorKey — mint/cyan/coral/sand/lilac/forest.
            Изменение атомарно. Архивный переезд — 409, включая пустой PATCH.
            """, responses = {
            @ApiResponse(responseCode = "200", description = "Актуальная папка", content = @Content(schema = @Schema(implementation = TaskFolderResponse.class))),
            @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: недопустимое имя, colorKey или null; fieldErrors указывает поле", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<TaskFolderResponse> update(
            @Parameter(description = "UUID переезда") @PathVariable UUID projectId,
            @Parameter(description = "UUID папки") @PathVariable UUID folderId,
            @Valid @RequestBody UpdateTaskFolderRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(service.update(projectId, folderId, request, user.getId()));
    }

    @DeleteMapping("/{folderId}")
    @Operation(summary = "Удалить папку, сохранив задачи", description = """
            Доступно любому участнику. Подтверждение удаления непустой папки выполняет клиент.
            Удаление папки и очистка folderId у всех её задач атомарны. Задачи, сроки, ответственные,
            выполнение, шаблонное происхождение и связи с вещами сохраняются.
            Повтор удаления в доступном проекте — 204. Папка другого проекта — 404.
            Архивный переезд запрещает удаление, включая повторы: 409.
            """, responses = {
            @ApiResponse(responseCode = "204", description = "Папка удалена или уже отсутствует", content = @Content),
            @ApiResponse(responseCode = "409", description = "DATA_CONFLICT: переезд в архиве", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @Parameter(description = "UUID переезда") @PathVariable UUID projectId,
            @Parameter(description = "UUID папки") @PathVariable UUID folderId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user) {
        service.delete(projectId, folderId, user.getId());
        return ResponseEntity.noContent().build();
    }
}
