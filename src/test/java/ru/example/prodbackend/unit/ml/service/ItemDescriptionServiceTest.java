package ru.example.prodbackend.unit.ml.service;

import ru.example.prodbackend.ml.service.ItemDescriptionService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.List;
import java.util.UUID;
import ru.example.prodbackend.ml.service.MlRateLimiter;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ItemDescriptionServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final MlRateLimiter limiter = mock(MlRateLimiter.class);
    @Test
    void invalidPhotoIsRejectedBeforeCallingLlmInBothScenarios() {
        var client = mock(LlmProxyClient.class);
        var service = new ItemDescriptionService(new PhotoValidator(), client, limiter);
        var empty = new MockMultipartFile("photo", new byte[0]);
        assertThatThrownBy(() -> service.describe(userId, empty))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        assertThatThrownBy(() -> service.describeItems(userId, empty))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client, limiter);
    }

    @Test
    void returnsSuggestionAfterPhotoValidation() {
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ItemDescriptionResponse("Кружка", List.of());
        when(client.describe(bytes, "image/jpeg")).thenReturn(response);
        assertThat(new ItemDescriptionService(new PhotoValidator(), client, limiter)
                .describe(userId, new MockMultipartFile("photo", bytes))).isEqualTo(response);
        var order = inOrder(limiter, client);
        order.verify(limiter).acquire(userId);
        order.verify(client).describe(bytes, "image/jpeg");
    }
    @Test
    void listScenarioValidatesPhotoBeforeCallingClient() {
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ru.example.prodbackend.ml.dto.ItemsDescriptionResponse(
                List.of(new ItemDescriptionResponse("Кружка", List.of())));
        when(client.describeItems(bytes, "image/jpeg")).thenReturn(response);
        var service = new ItemDescriptionService(new PhotoValidator(), client, limiter);
        assertThat(service.describeItems(userId, new MockMultipartFile("photo", bytes))).isEqualTo(response);
        var order = inOrder(limiter, client);
        order.verify(limiter).acquire(userId);
        order.verify(client).describeItems(bytes, "image/jpeg");
        clearInvocations(client);
        assertThatThrownBy(() -> service.describeItems(userId, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client);
    }

}
