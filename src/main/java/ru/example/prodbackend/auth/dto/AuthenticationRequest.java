package ru.example.prodbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {


    @Schema(description = "Email пользователя, валидации нету, главное не пустой", example = "user@example.com")
    @NotBlank(message = "email is blank")
    private String email;

    @Schema(description = "Пароль, валидации нету, главное не пустой", example = "P@ssw0rd!")
    @NotBlank(message = "password is blank")
    private String password;
}
