package ru.example.prodbackend.ml.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static ru.example.prodbackend.ml.RoomPackingPlanFixtures.*;

class RoomPackingPlanServiceTest {
    private final LlmProxyClient client = mock(LlmProxyClient.class);
    private final RoomPackingPlanService service = new RoomPackingPlanService(new PhotoValidator(), client);
    private final byte[] jpeg = {(byte)255, (byte)216, (byte)255};

    @Test
    void acceptsTextPhotoAndCombination() {
        var response = response();
        when(client.planRoomPacking("Кухня", null, null)).thenReturn(response);
        when(client.planRoomPacking("", jpeg, "image/jpeg")).thenReturn(response);
        when(client.planRoomPacking("Кухня", jpeg, "image/jpeg")).thenReturn(response);
        assertThat(service.plan(" Кухня ", null)).isEqualTo(response);
        var photo = new MockMultipartFile("photo", jpeg);
        assertThat(service.plan(null, photo)).isEqualTo(response);
        assertThat(service.plan("Кухня", photo)).isEqualTo(response);
    }

    @Test
    void inputValidationStopsBeforeClientCall() {
        code(null, null, "ROOM_INPUT_REQUIRED");
        code(" \n ", null, "ROOM_INPUT_REQUIRED");
        code("x".repeat(4001), null, "DESCRIPTION_TOO_LONG");
        code("x".repeat(4001), new MockMultipartFile("photo", jpeg), "DESCRIPTION_TOO_LONG");
        code("Кухня", new MockMultipartFile("photo", new byte[0]), "EMPTY_IMAGE");
        code("Кухня", new MockMultipartFile("photo", new byte[]{1}), "UNSUPPORTED_IMAGE_FORMAT");
        code(null, new MockMultipartFile("photo", new byte[10 * 1024 * 1024 + 1]), "IMAGE_TOO_LARGE");
        verifyNoInteractions(client);
    }

    @Test
    void trimsBeforeCheckingDescriptionLimit() {
        service.plan("  " + "x".repeat(4000) + "  ", null);
        verify(client).planRoomPacking("x".repeat(4000), null, null);
    }

    private void code(String text, MockMultipartFile photo, String expected) {
        assertThatThrownBy(() -> service.plan(text, photo)).isInstanceOfSatisfying(MlException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
    }
}
