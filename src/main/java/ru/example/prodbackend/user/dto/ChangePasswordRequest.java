package ru.example.prodbackend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    @Schema(example = "P@ssw0rd!", description = "Текущий пароль пользователя")
    @NotBlank
    private String currentPassword;

    @Schema(example = "P@ssw0rd!", description = "Новый пароль: не менее 8 символов")
    @NotBlank
    @Size(min = 8, message = "password length min = 8")
    private String newPassword;
}
