package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.RoomPackingPlanRequest;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.RoomPackingPlanService;
import ru.example.prodbackend.user.entity.UserEntity;

@RestController
@RequestMapping(value = "/api/ml/room-packing-plan", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = SwaggerTags.PACKING)
@SecurityRequirement(name = "jwtAuth")
public class RoomPackingPlanController {
    private final RoomPackingPlanService service;

    @Operation(summary = "Спланировать упаковку комнаты",
            description = "Текст до 4000 символов после удаления крайних пробелов. JWT обязателен, проект не требуется. "
                    + "Рекомендации для типовых коробок около 40×30×30 см с загрузкой до 10 кг. "
                    + "Описание передаётся внешнему LLM Proxy. Результат не сохраняется. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Рекомендации без сохранения и создания коробок",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomPackingPlanResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_MULTIPART: Некорректные поля multipart; INVALID_PHOTO: Не удалось прочитать фотографию; INVALID_REQUEST_BODY: Некорректное тело JSON", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE: Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_FORMAT: Используйте JPEG, PNG или WebP; UNSUPPORTED_MEDIA_TYPE: Неподдерживаемый Content-Type запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "ROOM_INPUT_REQUIRED: Нужны описание или фотография; DESCRIPTION_TOO_LONG: Описание длиннее 4000 символов; EMPTY_IMAGE: Фотография пуста; ROOM_PLAN_NOT_GENERATED: План не сформирован", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "429", description = "ML_RATE_LIMIT_EXCEEDED: Превышен общий лимит ML-запросов пользователя (по умолчанию 15 за 60 секунд)",
                            headers = @Header(name = "Retry-After", description = "Количество секунд до повторной попытки",
                                    schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "60")),
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "502", description = "LLM_INVALID_RESPONSE: Некорректный ответ модели; LLM_UPSTREAM_ERROR: Ошибка LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE: LLM Proxy не настроен, недоступен или перегружен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "504", description = "LLM_TIMEOUT: Превышено время ожидания LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public RoomPackingPlanResponse planText(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @RequestBody RoomPackingPlanRequest request) {
        return service.plan(user.getId(), request.getDescription(), null);
    }

    @Operation(summary = "Спланировать упаковку комнаты",
            description = "JPEG/PNG/WebP до 10 МиБ, текст description до 4000 символов или оба источника. "
                    + "Должен быть хотя бы один источник. Фото и текст передаются внешнему LLM Proxy. "
                    + "JWT обязателен, проект не требуется. Рекомендации приблизительны, данные не изменяются. "
                    + "Коробки около 40×30×30 см с загрузкой до 10 кг. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Рекомендации без сохранения и создания коробок",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = RoomPackingPlanResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_MULTIPART: Некорректные поля multipart; INVALID_PHOTO: Не удалось прочитать фотографию; INVALID_REQUEST_BODY: Некорректное тело JSON", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE: Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_FORMAT: Используйте JPEG, PNG или WebP; UNSUPPORTED_MEDIA_TYPE: Неподдерживаемый Content-Type запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "ROOM_INPUT_REQUIRED: Нужны описание или фотография; DESCRIPTION_TOO_LONG: Описание длиннее 4000 символов; EMPTY_IMAGE: Фотография пуста; ROOM_PLAN_NOT_GENERATED: План не сформирован", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "429", description = "ML_RATE_LIMIT_EXCEEDED: Превышен общий лимит ML-запросов пользователя (по умолчанию 15 за 60 секунд)",
                            headers = @Header(name = "Retry-After", description = "Количество секунд до повторной попытки",
                                    schema = @Schema(type = "integer", format = "int64", minimum = "1", example = "60")),
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "502", description = "LLM_INVALID_RESPONSE: Некорректный ответ модели; LLM_UPSTREAM_ERROR: Ошибка LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE: LLM Proxy не настроен, недоступен или перегружен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "504", description = "LLM_TIMEOUT: Превышено время ожидания LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @RoomPackingMultipartBody
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomPackingPlanResponse planPhoto(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @Parameter(description = "Фотография JPEG, PNG или WebP размером до 10 МиБ") @RequestPart(value = "photo", required = false) MultipartFile photo,
            @Parameter(hidden = true)
            @RequestParam(value = "description", required = false) Object description,
            MultipartHttpServletRequest request) throws IOException, ServletException {
        var descriptions = request.getParameterValues("description");
        var photoFields = request.getParameterValues("photo");
        var parts = request.getParts();
        // Bind description without coercion so a file in this field is rejected as invalid multipart.
        if ((description != null && !(description instanceof String))
                || request.getMultiFileMap().keySet().stream().anyMatch(name -> !"photo".equals(name))
                || request.getFiles("photo").size() > 1
                || request.getParameterMap().keySet().stream().anyMatch(name -> !"description".equals(name) && !"photo".equals(name))
                // curl/Swagger can send an unselected optional photo as an empty text field.
                || (photoFields != null && (photoFields.length != 1 || !photoFields[0].isEmpty()
                        || !request.getFiles("photo").isEmpty()))
                || (descriptions != null && descriptions.length > 1)
                || parts.stream().anyMatch(part -> !"photo".equals(part.getName()) && !"description".equals(part.getName()))
                || parts.stream().filter(part -> "photo".equals(part.getName())).count() > 1
                || parts.stream().filter(part -> "description".equals(part.getName())).count() > 1) {
            throw new MlException(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "Передайте description и/или одну фотографию photo");
        }
        return service.plan(user.getId(), (String)description, photo);
    }
}
