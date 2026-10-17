package ru.example.prodbackend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {

    @Schema(description = "Адрес электронной почты: непустая строка до 255 символов; формат адреса не проверяется", example = "user@example.com")
    @Size(max = 255, message = "email is too long")
    @NotBlank(message = "email is blank")
    private String email;

    @Schema(description = "Имя пользователя", example = "Иван")
    @NotBlank(message = "first name is blank")
    @Size(max = 255, message = "first name is too long")
    private String firstName;

    @Schema(description = "Фамилия пользователя", example = "Иванов")
    @NotBlank(message = "last name is blank")
    @Size(max = 255, message = "last name is too long")
    private String lastName;


    @Schema(description = "Пароль: непустая строка длиной не менее 8 символов", example = "P@ssw0rd!")
    @Size(min = 8, message = "password length min = 8")
    @NotBlank(message = "password is blank")
    private String password;

}
