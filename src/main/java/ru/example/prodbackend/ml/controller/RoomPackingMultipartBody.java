package ru.example.prodbackend.ml.controller;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;
import ru.example.prodbackend.ml.dto.RoomPackingMultipartRequest;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@RequestBody(
        description = "Описание комнаты, фотография или оба источника. Нужен хотя бы один источник.",
        required = true,
        content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                schema = @Schema(implementation = RoomPackingMultipartRequest.class)))
@interface RoomPackingMultipartBody {
}
