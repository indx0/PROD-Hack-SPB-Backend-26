package ru.example.prodbackend.unit.ml.service;

import ru.example.prodbackend.ml.service.UnpackingInventoryService;
import ru.example.prodbackend.ml.service.UnpackingVerificationService;
import ru.example.prodbackend.ml.service.MlRateLimiter;

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
    private final MlRateLimiter limiter = mock(MlRateLimiter.class);
    private final LlmProxyClient client = mock(LlmProxyClient.class);
    private final UUID user = UUID.randomUUID(), project = UUID.randomUUID(), box = UUID.randomUUID();
    private final InventoryItem cup = new InventoryItem(UUID.randomUUID(), "Кружка");
    private final InventoryItem charger = new InventoryItem(UUID.randomUUID(), "Зарядка");
    private final byte[] bytes = {(byte)255, (byte)216, (byte)255};
    private final MockMultipartFile photo = new MockMultipartFile("photo", bytes);
    private final UnpackingVerificationService service = new UnpackingVerificationService(inventories, new PhotoValidator(), client, limiter);

    @Test
    void buildsPartialReportFromSnapshotAndDoesNotInferLoss() {
        var snapshot = List.of(cup, charger);
        when(inventories.snapshot(user, project, box)).thenReturn(snapshot);
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(
                new ObservedItem(charger.getItemId(), "Зарядное устройство"), new ObservedItem(null, "Тарелка")));
        var result = service.verify(user, project, box, photo);
        assertThat(result.getBoxId()).isEqualTo(box);
        assertThat(result.getMatched()).singleElement().satisfies(item -> {
            assertThat(item.getItemId()).isEqualTo(charger.getItemId());
            assertThat(item.getName()).isEqualTo("Зарядка");
            assertThat(item.getPhotoDescription()).isEqualTo("Зарядное устройство");
        });
        assertThat(result.getNotSeen()).containsExactly(cup);
        assertThat(result.getUnmatched()).containsExactly("Тарелка");
        var order = inOrder(inventories, limiter, client);
        order.verify(inventories).snapshot(user, project, box);
        order.verify(limiter).acquire(user);
        order.verify(client).verifyUnpacking(bytes, "image/jpeg", snapshot);
        verifyNoMoreInteractions(inventories, limiter, client);
    }

    @Test
    void supportsFullMatchAndZeroMatches() {
        var snapshot = List.of(cup, charger);
        when(inventories.snapshot(user, project, box)).thenReturn(snapshot);
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(
                new ObservedItem(cup.getItemId(), "Кружка"), new ObservedItem(charger.getItemId(), "Зарядка")));
        var full = service.verify(user, project, box, photo);
        assertThat(full.getMatched()).hasSize(2);
        assertThat(full.getNotSeen()).isEmpty();
        assertThat(full.getUnmatched()).isEmpty();
        when(client.verifyUnpacking(bytes, "image/jpeg", snapshot)).thenReturn(List.of(new ObservedItem(null, "Книга")));
        var none = service.verify(user, project, box, photo);
        assertThat(none.getMatched()).isEmpty();
        assertThat(none.getNotSeen()).containsExactly(cup, charger);
        assertThat(none.getUnmatched()).containsExactly("Книга");
    }

    @Test
    void accessFailurePrecedesPhotoReadingAndLlmCall() {
        var photos = mock(PhotoValidator.class);
        when(inventories.snapshot(user, project, box)).thenThrow(new MoveProjectNotFoundException("missing"));
        assertThatThrownBy(() -> new UnpackingVerificationService(inventories, photos, client, limiter)
                .verify(user, project, box, null)).isInstanceOf(MoveProjectNotFoundException.class);
        verifyNoInteractions(photos, limiter, client);
    }

    @Test
    void invalidPhotoNeverReachesClient() {
        when(inventories.snapshot(user, project, box)).thenReturn(List.of(cup));
        assertThatThrownBy(() -> service.verify(user, project, box, new MockMultipartFile("photo", new byte[0])))
                .isInstanceOf(ru.example.prodbackend.ml.exception.MlException.class);
        verifyNoInteractions(limiter, client);
    }
}
