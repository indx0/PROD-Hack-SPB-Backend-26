package ru.example.prodbackend.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationResponse {

    @Schema(description = "Токен доступа JWT; JSON-поле access_token")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "Токен обновления сессии; JSON-поле refresh_token")
    @JsonProperty("refresh_token")
    private String refreshToken;
}

