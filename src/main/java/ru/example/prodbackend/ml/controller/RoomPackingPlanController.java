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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.RoomPackingPlanRequest;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.RoomPackingPlanService;

@RestController
@RequestMapping(value = "/api/ml/room-packing-plan", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Room packing", description = "Планирование упаковки комнаты по тексту и/или фотографии")
@SecurityRequirement(name = "jwtAuth")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Рекомендации без сохранения и создания коробок",
                content = @Content(schema = @Schema(implementation = RoomPackingPlanResponse.class))),
        @ApiResponse(responseCode = "400", description = "Некорректный JSON или multipart; разрешены description и не более одного photo",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Требуется JWT, проект не нужен",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "413", description = "Фото больше 10 МиБ или запрос больше 11 МиБ",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "415", description = "Неподдерживаемый формат фото или Content-Type запроса",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "ROOM_INPUT_REQUIRED, DESCRIPTION_TOO_LONG, EMPTY_IMAGE или ROOM_PLAN_NOT_GENERATED",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "502", description = "Некорректный ответ модели или ошибка LLM Proxy",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "LLM Proxy не настроен, недоступен или перегружен",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "504", description = "Таймаут LLM Proxy",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class RoomPackingPlanController {
    private final RoomPackingPlanService service;

    @Operation(summary = "Спланировать упаковку комнаты",
            description = "Текст до 4000 символов после удаления крайних пробелов. JWT обязателен, проект не требуется. "
                    + "Рекомендации для типовых коробок около 40×30×30 см с загрузкой до 10 кг. "
                    + "Описание передаётся внешнему LLM Proxy. Результат не сохраняется. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд.")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public RoomPackingPlanResponse planText(@RequestBody RoomPackingPlanRequest request) {
        return service.plan(request.description(), null);
    }

    @Operation(summary = "Спланировать упаковку комнаты",
            description = "JPEG/PNG/WebP до 10 МиБ, текст description до 4000 символов или оба источника. "
                    + "Должен быть хотя бы один источник. Фото и текст передаются внешнему LLM Proxy. "
                    + "JWT обязателен, проект не требуется. Рекомендации приблизительны, данные не изменяются. "
                    + "Коробки около 40×30×30 см с загрузкой до 10 кг. "
                    + "Ожидание до 120 секунд; рекомендуемый таймаут клиента — 150 секунд.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RoomPackingPlanResponse planPhoto(
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @Parameter(schema = @Schema(type = "string", maxLength = 4000))
            @RequestParam(value = "description", required = false) Object description,
            MultipartHttpServletRequest request) throws java.io.IOException, jakarta.servlet.ServletException {
        var descriptions = request.getParameterValues("description");
        var parts = request.getParts();
        // Bind description without coercion so a file in this field is rejected as invalid multipart.
        if ((description != null && !(description instanceof String))
                || request.getMultiFileMap().keySet().stream().anyMatch(name -> !"photo".equals(name))
                || request.getFiles("photo").size() > 1
                || request.getParameterMap().keySet().stream().anyMatch(name -> !"description".equals(name))
                || (descriptions != null && descriptions.length > 1)
                || parts.stream().anyMatch(part -> !"photo".equals(part.getName()) && !"description".equals(part.getName()))
                || parts.stream().filter(part -> "photo".equals(part.getName())).count() > 1
                || parts.stream().filter(part -> "description".equals(part.getName())).count() > 1) {
            throw new MlException(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "Передайте description и/или одну фотографию photo");
        }
        return service.plan((String)description, photo);
    }
}
