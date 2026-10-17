package ru.example.prodbackend.ml.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.ItemDescriptionResponse;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import ru.example.prodbackend.move.service.ProjectAccessService;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ItemDescriptionServiceTest {
    @Test
    void checksOwnershipBeforeReadingOrSendingPhoto() {
        var access = mock(ProjectAccessService.class);
        var photos = mock(PhotoValidator.class);
        var client = mock(LlmProxyClient.class);
        var user = UUID.randomUUID(); var project = UUID.randomUUID();
        when(access.getAccessibleProject(project, user)).thenThrow(new MoveProjectNotFoundException("missing"));
        assertThatThrownBy(() -> new ItemDescriptionService(access, photos, client).describe(user, project, null))
                .isInstanceOf(MoveProjectNotFoundException.class);
        assertThatThrownBy(() -> new ItemDescriptionService(access, photos, client).describeItems(user, project, null))
                .isInstanceOf(MoveProjectNotFoundException.class);
        verifyNoInteractions(photos, client);
    }

    @Test
    void returnsSuggestionAfterAccessAndPhotoValidation() {
        var access = mock(ProjectAccessService.class);
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ItemDescriptionResponse("Кружка", List.of());
        when(client.describe(bytes, "image/jpeg")).thenReturn(response);
        var user = UUID.randomUUID(); var project = UUID.randomUUID();
        assertThat(new ItemDescriptionService(access, new PhotoValidator(), client)
                .describe(user, project, new MockMultipartFile("photo", bytes))).isEqualTo(response);
        var ordered = inOrder(access, client);
        ordered.verify(access).getAccessibleProject(project, user);
        ordered.verify(client).describe(bytes, "image/jpeg");
    }
    @Test
    void listScenarioValidatesAccessAndPhotoBeforeCallingClient() {
        var access = mock(ProjectAccessService.class);
        var client = mock(LlmProxyClient.class);
        byte[] bytes = {(byte)255, (byte)216, (byte)255};
        var response = new ru.example.prodbackend.ml.dto.ItemsDescriptionResponse(
                List.of(new ItemDescriptionResponse("Кружка", List.of())));
        when(client.describeItems(bytes, "image/jpeg")).thenReturn(response);
        var user = UUID.randomUUID(); var project = UUID.randomUUID();
        var service = new ItemDescriptionService(access, new PhotoValidator(), client);
        assertThat(service.describeItems(user, project, new MockMultipartFile("photo", bytes))).isEqualTo(response);
        var ordered = inOrder(access, client);
        ordered.verify(access).getAccessibleProject(project, user);
        ordered.verify(client).describeItems(bytes, "image/jpeg");
        clearInvocations(client);
        assertThatThrownBy(() -> service.describeItems(user, project, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client);
    }

}
