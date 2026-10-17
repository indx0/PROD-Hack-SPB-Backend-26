package ru.example.prodbackend.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.example.prodbackend.auth.dto.AuthenticationRequest;
import ru.example.prodbackend.auth.dto.AuthenticationResponse;
import ru.example.prodbackend.auth.dto.RegisterRequest;
import ru.example.prodbackend.auth.dto.refresh.RefreshTokenRequest;
import ru.example.prodbackend.auth.service.AuthenticationService;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;

@Tag(name = SwaggerTags.AUTH)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService authenticationService;

    @Operation(
            summary = "Зарегистрировать пользователя",
            description = "Создает пользователя и возвращает пару access и refresh токенов",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешно, пользователь создан, возвращается пара токенов"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "USER_ALREADY_EXISTS: Пользователь с таким email уже существует", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Поля запроса не прошли валидацию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(authenticationService.register(request));
    }


    @Operation(
            summary = "Войти в аккаунт",
            description = "Проверяет email и пароль и возвращает пару access и refresh токенов",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешно, возвращается пара токенов"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Неверный адрес электронной почты или пароль", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "NOT_FOUND: Пользователь не найден после проверки учётных данных", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Поля запроса не прошли валидацию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @Valid @RequestBody AuthenticationRequest request
    ) {
        return ResponseEntity.ok(authenticationService.authenticate(request));
    }

    @Operation(
            summary = "Обновить пару токенов",
            description = "Принимает refresh токен и возвращает новую пару токенов. старый refresh токен больше не работает",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешно, возвращается два новых токена"),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN: Токен обновления недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Поля запроса не прошли валидацию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        return ResponseEntity.ok(authenticationService.updateTokens(request));
    }

    @Operation(
            summary = "Выйти из аккаунта",
            description = "Удаляет refresh токен текущего устройства. сессии остальных устройств сохраняются",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Успешно, refresh токен удален или уже отсутствует", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Поля запроса не прошли валидацию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authenticationService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Выйти со всех устройств",
            description = "Принимает действующий refresh токен и удаляет все refresh токены пользователя",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Успешно, все refresh токены пользователя удалены", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "INVALID_REFRESH_TOKEN: Токен обновления недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Поля запроса не прошли валидацию", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            @Valid @RequestBody RefreshTokenRequest request
    ) {
        authenticationService.logoutAll(request);

        return ResponseEntity.noContent().build();
    }
}
