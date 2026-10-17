package ru.example.prodbackend.ml.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProxyProperties.class)
public class LlmProxyConfig {
    @Bean
    RestClient llmRestClient(LlmProxyProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService llmExecutor() {
        // No queue: bound retained image payloads and reject overload immediately.
        return new ThreadPoolExecutor(0, 20, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "llm-proxy-request");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
}
