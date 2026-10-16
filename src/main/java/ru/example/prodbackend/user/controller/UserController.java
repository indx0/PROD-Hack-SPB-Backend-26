package ru.example.prodbackend.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.example.prodbackend.user.dto.ChangePasswordRequest;
import ru.example.prodbackend.user.dto.UpdateProfileRequest;
import ru.example.prodbackend.user.dto.UpdateProfileResponse;
import ru.example.prodbackend.exception.dto.ErrorResponse;
import ru.example.prodbackend.user.dto.UserProfileResponse;
import ru.example.prodbackend.user.entity.UserEntity;
import ru.example.prodbackend.user.service.UserService;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(
            summary = "получение профиля текущего пользователя",
            description = "возвращает идентификатор, email и роль пользователя, авторизованного по access токену",
            responses = {
                    @ApiResponse(responseCode = "200", description = "успешно, возвращается профиль текущего пользователя", content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
                    @ApiResponse(responseCode = "401", description = "access токен отсутствует, некорректный или истек", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            }
    )
    @SecurityRequirement(name = "jwtAuth")
    @GetMapping("/me")
    ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal UserEntity user) {
        return ResponseEntity.ok(userService.getProfile(user));
    }

    @Operation(summary = "редактирование профиля текущего пользователя",
            description = "Изменяет email, имя и фамилию. При изменении email отзывает все refresh токены и возвращает новую пару; клиент должен сразу использовать новые токены.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Профиль обновлен", content = @Content(schema = @Schema(implementation = UpdateProfileResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректное тело запроса", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Требуется действующий access токен", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Email уже занят", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Ошибка валидации", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @PatchMapping("/me")
    public ResponseEntity<UpdateProfileResponse> updateProfile(
            @AuthenticationPrincipal UserEntity user, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user, request));
    }

    @Operation(summary = "изменение пароля текущего пользователя",
            description = "Проверяет текущий пароль и отзывает все refresh токены. Access токены сохраняются до истечения срока действия.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Пароль изменен", content = @Content),
                    @ApiResponse(responseCode = "400", description = "Некорректное тело запроса", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Некорректный access токен или текущий пароль", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "422", description = "Ошибка валидации", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @SecurityRequirement(name = "jwtAuth")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserEntity user, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(user, request);
        return ResponseEntity.noContent().build();
    }
}
