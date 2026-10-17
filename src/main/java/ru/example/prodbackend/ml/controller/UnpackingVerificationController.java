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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.ml.dto.UnpackingVerificationResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;
import ru.example.prodbackend.user.entity.UserEntity;
import java.util.UUID;

@RestController
@RequestMapping("/api/move-projects/{projectId}/ml/boxes/{boxId}")
@RequiredArgsConstructor
@Tag(name = "Photo inventory")
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
                    content = @Content(schema = @Schema(implementation = UnpackingVerificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный UUID или multipart: требуется единственное поле photo",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Требуется JWT",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Проект недоступен или коробки нет в проекте",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "Фото больше 10 МиБ или запрос больше 11 МиБ",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "415", description = "Неподдерживаемый формат фото",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "EMPTY_INVENTORY, INVENTORY_TOO_LARGE, EMPTY_IMAGE или IMAGE_NOT_RECOGNIZED",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "Некорректный ответ или ошибка LLM Proxy",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "LLM Proxy недоступен, не настроен или перегружен",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "504", description = "Таймаут LLM Proxy",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(value = "/unpacking-verification", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public UnpackingVerificationResponse verify(
            @Parameter(hidden = true) @AuthenticationPrincipal UserEntity user,
            @PathVariable UUID projectId, @PathVariable UUID boxId,
            @RequestPart("photo") MultipartFile photo, MultipartHttpServletRequest request) throws java.io.IOException, jakarta.servlet.ServletException {
        if (request.getMultiFileMap().size() != 1 || request.getFiles("photo").size() != 1
                || !request.getParameterMap().isEmpty() || request.getParts().stream().anyMatch(part -> !"photo".equals(part.getName()))) {
            throw new MlException(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART", "Передайте единственное поле photo");
        }
        return service.verify(user.getId(), projectId, boxId, photo);
    }
}
