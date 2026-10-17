package ru.example.prodbackend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.config.SwaggerTags;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.dto.ChangePasswordRequest;
import ru.example.prodbackend.user.dto.UpdateProfileRequest;
import ru.example.prodbackend.user.dto.UpdateProfileResponse;
import ru.example.prodbackend.user.dto.UserProfileResponse;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.service.UserService;

@Tag(name = SwaggerTags.USER)
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "Получить профиль текущего пользователя",
            description = "Возвращает идентификатор, email и роль пользователя, авторизованного по access токену",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешно, возвращается профиль текущего пользователя", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @GetMapping("/me")
    ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @Operation(summary = "Изменить профиль текущего пользователя",
            description = "Изменяет email, имя и фамилию. При изменении email отзывает все refresh токены и возвращает новую пару; клиент должен сразу использовать новые токены.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Профиль обновлен", content = @Content(mediaType = "application/json", schema = @Schema(implementation = UpdateProfileResponse.class))),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "USER_ALREADY_EXISTS: Email уже занят", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @AuthenticationPrincipal UserEntity user, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    @Operation(summary = "Изменить пароль текущего пользователя",
            description = "Проверяет текущий пароль и отзывает все refresh токены. Access токены сохраняются до истечения срока действия.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Пароль изменен", content = @Content),
                    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST_BODY: Тело отсутствует, содержит некорректный JSON или недопустимый формат поля", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: Токен доступа отсутствует, недействителен или истёк", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "VALIDATION_ERROR: Ошибка валидации", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserEntity user, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.noContent().build();
    }
}
