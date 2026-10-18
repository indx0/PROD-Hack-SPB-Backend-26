package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.PhotoValidator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomPackingPlanService {
    public static final int MAX_DESCRIPTION = 4000;
    private final PhotoValidator photos;
    private final LlmProxyClient client;
    private final MlRateLimiter rateLimiter;

    public RoomPackingPlanResponse plan(UUID userId, String description, MultipartFile photo) {
        String text = description == null ? "" : description.strip();
        if (text.length() > MAX_DESCRIPTION) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "DESCRIPTION_TOO_LONG", "Описание комнаты не должно превышать 4000 символов");
        if (text.isEmpty() && photo == null) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "ROOM_INPUT_REQUIRED", "Добавьте описание комнаты или фотографию");
        var image = photo == null ? null : photos.validate(photo);
        rateLimiter.acquire(userId);
        return client.planRoomPacking(text, image == null ? null : image.getBytes(), image == null ? null : image.getMime());
    }
}
