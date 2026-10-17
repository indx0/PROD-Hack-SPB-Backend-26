package ru.example.prodbackend.unit.ml.service;

import ru.example.prodbackend.ml.service.ItemDescriptionService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ItemDescriptionServiceTest {
    @Test
    void invalidPhotoIsRejectedBeforeCallingLlmInBothScenarios() {
        var client = mock(LlmProxyClient.class);
        var service = new ItemDescriptionService(new PhotoValidator(), client);
        var empty = new MockMultipartFile("photo", new byte[0]);
        assertThatThrownBy(() -> service.describe(empty))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        assertThatThrownBy(() -> service.describeItems(empty))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client);
    }

    @Test
    void returnsSuggestionAfterPhotoValidation() {
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ItemDescriptionResponse("Кружка", List.of());
        when(client.describe(bytes, "image/jpeg")).thenReturn(response);
        assertThat(new ItemDescriptionService(new PhotoValidator(), client)
                .describe(new MockMultipartFile("photo", bytes))).isEqualTo(response);
        verify(client).describe(bytes, "image/jpeg");
    }
    @Test
    void listScenarioValidatesPhotoBeforeCallingClient() {
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ru.example.prodbackend.ml.dto.ItemsDescriptionResponse(
                List.of(new ItemDescriptionResponse("Кружка", List.of())));
        when(client.describeItems(bytes, "image/jpeg")).thenReturn(response);
        var service = new ItemDescriptionService(new PhotoValidator(), client);
        assertThat(service.describeItems(new MockMultipartFile("photo", bytes))).isEqualTo(response);
        verify(client).describeItems(bytes, "image/jpeg");
        clearInvocations(client);
        assertThatThrownBy(() -> service.describeItems(new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client);
    }

}
