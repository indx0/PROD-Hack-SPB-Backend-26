package ru.example.prodbackend.ml.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Только описание multipart для OpenAPI; фактическое связывание запроса выполняет контроллер. */
@Schema(description = "Данные для планирования упаковки: описание, фотография или оба источника")
public record RoomPackingMultipartRequest(
        @Schema(type = "string", format = "binary", description = "Фотография JPEG, PNG или WebP до 10 МиБ; необязательна при непустом description")
        String photo,
        @Schema(description = "Описание комнаты до 4000 символов после удаления пробелов по краям; обязательно без фотографии",
                maxLength = 4000, example = "Кухня: посуда, кастрюли и мелкая бытовая техника")
        String description) { }
