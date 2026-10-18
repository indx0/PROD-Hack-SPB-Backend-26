package ru.example.prodbackend.unit.ml.service;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.dto.ItemsDescriptionResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.service.ItemDescriptionService;
import ru.example.prodbackend.ml.service.MlRateLimiter;
import ru.example.prodbackend.ml.validation.PhotoValidator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemDescriptionServiceTest {
    private final UUID userId = UUID.randomUUID();
    private final MlRateLimiter limiter = mock(MlRateLimiter.class);
    @Test
    void invalidPhotoIsRejectedBeforeCallingLlmInBothScenarios() {
        var client = mock(LlmProxyClient.class);
        var service = new ItemDescriptionService(new PhotoValidator(), client, limiter);
        var empty = new MockMultipartFile("photo", new byte[0]);
        assertThatThrownBy(() -> service.describe(userId, empty))
                .isInstanceOf(MlException.class);
        assertThatThrownBy(() -> service.describeItems(userId, empty))
                .isInstanceOf(MlException.class);
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
        var response = new ItemsDescriptionResponse(
                List.of(new ItemDescriptionResponse("Кружка", List.of())));
        when(client.describeItems(bytes, "image/jpeg")).thenReturn(response);
        var service = new ItemDescriptionService(new PhotoValidator(), client, limiter);
        assertThat(service.describeItems(userId, new MockMultipartFile("photo", bytes))).isEqualTo(response);
        var order = inOrder(limiter, client);
        order.verify(limiter).acquire(userId);
        order.verify(client).describeItems(bytes, "image/jpeg");
        clearInvocations(client);
        assertThatThrownBy(() -> service.describeItems(userId, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(MlException.class);
        verifyNoInteractions(client);
    }

}
