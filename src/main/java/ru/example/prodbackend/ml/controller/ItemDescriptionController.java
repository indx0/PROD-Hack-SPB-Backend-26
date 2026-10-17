package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.ItemDescriptionService;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Tag(name = SwaggerTags.PHOTO)
@SecurityRequirement(name = "jwtAuth")
public class ItemDescriptionController {
    private final ItemDescriptionService service;

    @Operation(summary = "Распознать предмет по фотографии",
            description = "JWT обязателен; проект не требуется. JPEG, PNG или WebP до 10 МиБ. Фото передаётся внешнему LLM Proxy. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд. "
                    + "Результат не сохраняется: проверьте его и вызовите создание вещи отдельно.",
            responses = {
                    @ApiResponse(responseCode = "400", description = "INVALID_MULTIPART: Некорректные поля multipart; INVALID_PHOTO: Не удалось прочитать фотографию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE: Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_FORMAT: Используйте JPEG, PNG или WebP; UNSUPPORTED_MEDIA_TYPE: Неподдерживаемый Content-Type запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "EMPTY_IMAGE: Фотография пуста; IMAGE_NOT_RECOGNIZED: Предметы не распознаны", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "502", description = "LLM_INVALID_RESPONSE: Некорректный ответ модели; LLM_UPSTREAM_ERROR: Ошибка LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE: LLM Proxy не настроен, недоступен или перегружен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "504", description = "LLM_TIMEOUT: Превышено время ожидания LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @ApiResponse(responseCode = "200", description = "Название и предложенные метки",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemDescriptionResponse.class)))
    @PostMapping(value = "/item-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemDescriptionResponse describe(
            @Parameter(description = "Фотография JPEG, PNG или WebP размером до 10 МиБ") @RequestPart("photo") MultipartFile photo,
            MultipartHttpServletRequest request) {
        requireSinglePhoto(request);
        return service.describe(photo);
    }

    @Operation(summary = "Распознать несколько предметов по фотографии",
            description = "JWT обязателен; проект не требуется. JPEG, PNG или WebP до 10 МиБ. Один файл photo передаётся внешнему LLM Proxy. "
                    + "Возвращает от 1 до 100 предметов с названиями и метками в порядке модели. "
                    + "Одинаковые предметы представлены отдельно; полнота описи и точный подсчёт не гарантируются. "
                    + "Результат не сохраняется. Ожидание до 120 секунд, таймаут клиента — 150 секунд.",
            responses = {
                    @ApiResponse(responseCode = "400", description = "INVALID_MULTIPART: Некорректные поля multipart; INVALID_PHOTO: Не удалось прочитать фотографию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "413", description = "IMAGE_TOO_LARGE: Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "415", description = "UNSUPPORTED_IMAGE_FORMAT: Используйте JPEG, PNG или WebP; UNSUPPORTED_MEDIA_TYPE: Неподдерживаемый Content-Type запроса", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "EMPTY_IMAGE: Фотография пуста; IMAGE_NOT_RECOGNIZED: Предметы не распознаны", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "502", description = "LLM_INVALID_RESPONSE: Некорректный ответ модели; LLM_UPSTREAM_ERROR: Ошибка LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "503", description = "LLM_UNAVAILABLE: LLM Proxy не настроен, недоступен или перегружен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "504", description = "LLM_TIMEOUT: Превышено время ожидания LLM Proxy", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @ApiResponse(responseCode = "200", description = "Список распознанных предметов",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ItemsDescriptionResponse.class),
                    examples = @ExampleObject(value = """
                            {"items":[{"name":"Белая кружка","flags":["fragile"]},
                                      {"name":"Белая кружка","flags":["fragile"]},
                                      {"name":"Зарядное устройство","flags":["first_day"]}]}
                            """)))
    @PostMapping(value = "/items-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemsDescriptionResponse describeItems(
            @Parameter(description = "Фотография JPEG, PNG или WebP размером до 10 МиБ") @RequestPart("photo") MultipartFile photo,
            MultipartHttpServletRequest request) {
        requireSinglePhoto(request);
        return service.describeItems(photo);
    }

    private void requireSinglePhoto(MultipartHttpServletRequest request) {
        if (request.getMultiFileMap().values().stream().mapToInt(java.util.List::size).sum() != 1) {
            throw new MlException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_MULTIPART", "Передайте ровно одну фотографию");
        }
    }
}
