package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.ItemDescriptionService;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Tag(name = "Photo inventory", description = "Опись одного или нескольких предметов по фотографии")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
            @ApiResponse(responseCode = "400", description = "Некорректный multipart или отсутствует photo", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Фотография больше 10 МиБ или запрос больше 11 МиБ", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Неподдерживаемый формат", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Файл пуст или предмет не распознан", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Некорректный ответ LLM", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "LLM Proxy не настроен или недоступен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Таймаут LLM", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
public class ItemDescriptionController {
    private final ItemDescriptionService service;

    @Operation(summary = "Распознать предмет по фотографии",
            description = "JWT обязателен; проект не требуется. JPEG, PNG или WebP до 10 МиБ. Фото передаётся внешнему LLM Proxy. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд. "
                    + "Результат не сохраняется: проверьте его и вызовите создание вещи отдельно.")
    @ApiResponse(responseCode = "200", description = "Название и предложенные метки",
            content = @Content(schema = @Schema(implementation = ItemDescriptionResponse.class)))
    @PostMapping(value = "/item-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemDescriptionResponse describe(
            @RequestPart("photo") MultipartFile photo,
            MultipartHttpServletRequest request) {
        requireSinglePhoto(request);
        return service.describe(photo);
    }

    @Operation(summary = "Распознать несколько предметов по фотографии",
            description = "JWT обязателен; проект не требуется. JPEG, PNG или WebP до 10 МиБ. Один файл photo передаётся внешнему LLM Proxy. "
                    + "Возвращает от 1 до 100 предметов с названиями и метками в порядке модели. "
                    + "Одинаковые предметы представлены отдельно; полнота описи и точный подсчёт не гарантируются. "
                    + "Результат не сохраняется. Ожидание до 120 секунд, таймаут клиента — 150 секунд.")
    @ApiResponse(responseCode = "200", description = "Список распознанных предметов",
            content = @Content(schema = @Schema(implementation = ItemsDescriptionResponse.class),
                    examples = @ExampleObject(value = """
                            {"items":[{"name":"Белая кружка","flags":["fragile"]},
                                      {"name":"Белая кружка","flags":["fragile"]},
                                      {"name":"Зарядное устройство","flags":["first_day"]}]}
                            """)))
    @PostMapping(value = "/items-description", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemsDescriptionResponse describeItems(
            @RequestPart("photo") MultipartFile photo,
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
