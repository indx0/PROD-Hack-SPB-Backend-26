package ru.example.prodbackend.auth.dto.refresh;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class RefreshTokenRequest {

    @Schema(description = "Токен обновления сессии")
    @NotBlank(message = "refresh token is blank")
    @JsonProperty("refresh_token")
    private String refreshToken;

}
