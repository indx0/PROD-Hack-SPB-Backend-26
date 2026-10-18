package ru.example.prodbackend.ml.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("llm.proxy")
@Validated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LlmProxyProperties {

    private URI url = URI.create("https://llm-proxy.spirit.t-education.com/chat/completions");

    @NotBlank
    private String model = "openai/gpt-6-luna";

    private String apiKey = "";

    @Min(1)
    private long connectTimeoutMs = 10000L;

    @Min(1)
    private long timeoutMs = 120000L;

    // Do not expose the API key through toString().
    @Override
    public String toString() { return "LlmProxyProperties[model=" + model + "]"; }
}
