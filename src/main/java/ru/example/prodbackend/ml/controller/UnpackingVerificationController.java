package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.UnpackingVerificationResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping("/api/move-projects/{projectId}/ml/boxes/{boxId}")
@RequiredArgsConstructor
@Tag(name = SwaggerTags.PHOTO)
@SecurityRequirement(name = "jwtAuth")
public class UnpackingVerificationController {
    private final UnpackingVerificationService service;

    @Operation(summary = "Сверить распакованные вещи с описью по фото",
            description = "Одна фотография JPEG, PNG или WebP до 10 МиБ и текущая опись из 1–100 вещей "
                    + "передаются внешнему LLM Proxy. Доступно владельцу и участникам проекта при любом статусе коробки. "
                    + "Опись фиксируется перед обращением к LLM. Фото и отчёт не сохраняются; данные не изменяются. "
                    + "notSeen не подтверждает потерю, unmatched не означает гарантированно лишнюю вещь. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Рекомендательный отчёт сверки",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UnpackingVerificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_PARAMETER: Некорректный UUID; INVALID_MULTIPART: Требуется единственное поле photo; INVALID_PHOTO: Не удалось прочитать фотографию",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "MOVE_PROJECT_NOT_FOUND: Проект недоступен; BOX_NOT_FOUND: Коробки нет в проекте",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE: Фотография больше 10 МиБ или запрос больше 11 МиБ",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_FORMAT: Используйте JPEG, PNG или WebP; UNSUPPORTED_MEDIA_TYPE: Неподдерживаемый Content-Type запроса",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "EMPTY_INVENTORY: Опись пуста; INVENTORY_TOO_LARGE: В описи больше 100 вещей; EMPTY_IMAGE: Фотография пуста; IMAGE_NOT_RECOGNIZED: Предметы не распознаны",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "ML_RATE_LIMIT_EXCEEDED: Превышен общий лимит ML-запросов пользователя (по умолчанию 15 за 60 секунд)",
                    headers = @Header(name = "Retry-After", description = "Количество секунд до повторной попытки",
                            schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "60")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "LLM_INVALID_RESPONSE: Некорректный ответ модели; LLM_UPSTREAM_ERROR: Ошибка LLM Proxy",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE: LLM Proxy не настроен, недоступен или перегружен",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "LLM_TIMEOUT: Превышено время ожидания LLM Proxy",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/unpacking-verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UnpackingVerificationResponse verify(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Идентификатор проекта переезда") @PathVariable UUID projectId, @Parameter(description = "Идентификатор коробки проекта") @PathVariable UUID boxId,
            @Parameter(description = "Фотография JPEG, PNG или WebP размером до 10 МиБ") @RequestPart("photo") MultipartFile photo, MultipartHttpServletRequest request) throws java.io.IOException, jakarta.servlet.ServletException {
        if (request.getMultiFileMap().size() != 1 || request.getFiles("photo").size() != 1
                || !request.getParameterMap().isEmpty() || request.getParts().stream().anyMatch(part -> !"photo".equals(part.getName()))) {
            throw new MlException(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "Передайте единственное поле photo");
        }
        return service.verify(user.getId(), projectId, boxId, photo);
    }
}
