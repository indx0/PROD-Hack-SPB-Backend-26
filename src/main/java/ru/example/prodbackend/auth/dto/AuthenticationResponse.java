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

    @Schema(description = "access_token, обрати внимание, что я всегда в ответе пишу через нижний слеш")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "refresh_token, обрати внимание, что я всегда в ответе пишу через нижний слеш")
    @JsonProperty("refresh_token")
    private String refreshToken;
}

