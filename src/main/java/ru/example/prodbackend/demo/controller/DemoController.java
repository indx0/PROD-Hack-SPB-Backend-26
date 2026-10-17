package ru.example.prodbackend.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;

@Tag(name = SwaggerTags.DEMO)
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    @Operation(
            summary = "Проверить доступность API",
            description = "Возвращает строку pong. Авторизация не требуется; данные не изменяются.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проверка выполнена")
            })
    @GetMapping("/ping")
    public ResponseEntity<String> ping(){
        return ResponseEntity.ok("pong");
    }

    @Operation(
            summary = "Проверить доступ после авторизации",
            description = "Возвращает строку auth pong при действующем токене доступа JWT; данные не изменяются.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Проверка выполнена"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @GetMapping("/authping")
    public ResponseEntity<String> authping() {
        return ResponseEntity.ok("auth pong");
    }
}
