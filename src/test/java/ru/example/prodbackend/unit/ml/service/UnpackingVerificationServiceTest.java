package ru.example.prodbackend.unit.ml.service;

import ru.example.prodbackend.ml.service.UnpackingInventoryService;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.exception.MoveProjectNotFoundException;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.InventoryItem;
import ru.example.prodbackend.ml.dto.ObservedItem;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnpackingVerificationServiceTest {
    private final UnpackingInventoryService inventories = mock(UnpackingInventoryService.class);
    private final LlmProxyClient client = mock(LlmProxyClient.class);
    private final UUID user = UUID.randomUUID(), project = UUID.randomUUID(), box = UUID.randomUUID();
    private final InventoryItem cup = new InventoryItem(UUID.randomUUID(), "Кружка");
    private final InventoryItem charger = new InventoryItem(UUID.randomUUID(), "Зарядка");
    private final byte[] bytes = {(byte)255, (byte)216, (byte)255};
    private final MockMultipartFile photo = new MockMultipartFile("photo", bytes);
    private final UnpackingVerificationService service = new UnpackingVerificationService(inventories, new PhotoValidator(), client);

    @Test
    void buildsPartialReportFromSnapshotAndDoesNotInferLoss() {
        var snapshot = List.of(cup, charger);
        when(inventories.snapshot(user, project, box)).thenReturn(snapshot);
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(
                new ObservedItem(charger.itemId(), "Зарядное устройство"), new ObservedItem(null, "Тарелка")));
        var result = service.verify(user, project, box, photo);
        assertThat(result.boxId()).isEqualTo(box);
        assertThat(result.matched()).singleElement().satisfies(item -> {
            assertThat(item.itemId()).isEqualTo(charger.itemId());
            assertThat(item.name()).isEqualTo("Зарядка");
            assertThat(item.photoDescription()).isEqualTo("Зарядное устройство");
        });
        assertThat(result.notSeen()).containsExactly(cup);
        assertThat(result.unmatched()).containsExactly("Тарелка");
        var order = inOrder(inventories, client);
        order.verify(inventories).snapshot(user, project, box);
        order.verify(client).verifyUnpacking(bytes, "image/jpeg", snapshot);
        verifyNoMoreInteractions(inventories, client);
    }

    @Test
    void supportsFullMatchAndZeroMatches() {
        var snapshot = List.of(cup, charger);
        when(inventories.snapshot(user, project, box)).thenReturn(snapshot);
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(
                new ObservedItem(cup.itemId(), "Кружка"), new ObservedItem(charger.itemId(), "Зарядка")));
        var full = service.verify(user, project, box, photo);
        assertThat(full.matched()).hasSize(2);
        assertThat(full.notSeen()).isEmpty();
        assertThat(full.unmatched()).isEmpty();
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(new ObservedItem(null, "Книга")));
        var none = service.verify(user, project, box, photo);
        assertThat(none.matched()).isEmpty();
        assertThat(none.notSeen()).containsExactly(cup, charger);
        assertThat(none.unmatched()).containsExactly("Книга");
    }

    @Test
    void accessFailurePrecedesPhotoReadingAndLlmCall() {
        var photos = mock(PhotoValidator.class);
        when(inventories.snapshot(user, project, box)).thenThrow(new MoveProjectNotFoundException("missing"));
        assertThatThrownBy(() -> new UnpackingVerificationService(inventories, photos, client)
                .verify(user, project, box, null)).isInstanceOf(MoveProjectNotFoundException.class);
        verifyNoInteractions(photos, client);
    }

    @Test
    void invalidPhotoNeverReachesClient() {
        when(inventories.snapshot(user, project, box)).thenReturn(List.of(cup));
        assertThatThrownBy(() -> service.verify(user, project, box, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(client);
    }
}
