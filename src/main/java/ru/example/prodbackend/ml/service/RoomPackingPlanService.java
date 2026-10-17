package ru.example.prodbackend.ml.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.client.LlmProxyClient;
import ru.example.prodbackend.ml.dto.RoomPackingPlanResponse;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.PhotoValidator;

@Service
@RequiredArgsConstructor
public class RoomPackingPlanService {
    public static final int MAX_DESCRIPTION = 4000;
    private final PhotoValidator photos;
    private final LlmProxyClient client;

    public RoomPackingPlanResponse plan(String description, MultipartFile photo) {
        String text = description == null ? "" : description.strip();
        if (text.length() > MAX_DESCRIPTION) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "DESCRIPTION_TOO_LONG", "Описание комнаты не должно превышать 4000 символов");
        if (text.isEmpty() && photo == null) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "ROOM_INPUT_REQUIRED", "Добавьте описание комнаты или фотографию");
        var image = photo == null ? null : photos.validate(photo);
        return client.planRoomPacking(text, image == null ? null : image.bytes(), image == null ? null : image.mime());
    }
}
