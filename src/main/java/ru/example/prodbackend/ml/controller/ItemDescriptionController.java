package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.ItemDescriptionService;
import ru.example.prodbackend.user.entity.UserEntity;
import java.util.UUID;

@RestController
@RequestMapping("/api/move-projects/{projectId}/ml")
@RequiredArgsConstructor
@Tag(name = "Photo inventory", description = "Опись одного главного предмета по фотографии")
@SecurityRequirement(name = "jwtAuth")
public class ItemDescriptionController {
    private final ItemDescriptionService service;

    @Operation(summary = "Распознать предмет по фотографии",
            description = "JPEG, PNG или WebP до 10 МиБ. Фото передаётся внешнему LLM Proxy. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд. "
                    + "Результат не сохраняется: проверьте его и вызовите создание вещи отдельно.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Название и предложенные метки"),
            @ApiResponse(responseCode = "400", description = "Некорректный multipart, UUID или отсутствует photo", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Проект не найден или недоступен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Неподдерживаемый формат", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Файл пуст или предмет не распознан", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Некорректный ответ LLM", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "LLM Proxy не настроен или недоступен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Таймаут LLM", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/item-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemDescriptionResponse describe(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId,
            @RequestPart("photo") MultipartFile photo,
            MultipartHttpServletRequest request) {
        if (request.getMultiFileMap().values().stream().mapToInt(java.util.List::size).sum() != 1) {
            throw new MlException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_MULTIPART", "Передайте ровно одну фотографию");
        }
        return service.describe(user.getId(), projectId, photo);
    }
}
