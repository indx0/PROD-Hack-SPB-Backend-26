package ru.example.prodbackend.ml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.net.URI;

@ConfigurationProperties("llm.proxy")
@Validated
public record LlmProxyProperties(
        @DefaultValue("https://llm-proxy.spirit.t-education.com/chat/completions") URI url,
        @DefaultValue("openai/gpt-6-luna") @NotBlank String model,
        @DefaultValue("") String apiKey,
        @DefaultValue("10000") @Min(1) long connectTimeoutMs,
        @DefaultValue("120000") @Min(1) long timeoutMs) {
    // Do not expose the API key through record-generated toString().
    @Override
    public String toString() { return "LlmProxyProperties[model=" + model + "]"; }
}
