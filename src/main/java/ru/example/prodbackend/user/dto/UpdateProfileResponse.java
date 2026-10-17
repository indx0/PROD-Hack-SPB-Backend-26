package ru.example.prodbackend.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileResponse {
    @Schema(description = "Актуальный профиль пользователя")
    private UserProfileResponse profile;

    @Schema(description = "Новый access токен при изменении email; иначе null")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "Новый refresh токен при изменении email; иначе null")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
