package ru.example.prodbackend.ml.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import ru.example.prodbackend.ml.exception.MlException;

@Component
public class PhotoValidator {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] PNG = {(byte)137, 80, 78, 71, 13, 10, 26, 10};

    @Value
    public static class Photo {

        private final byte[] bytes;

        private final String mime;
    }

    public Photo validate(MultipartFile file) {
        if (file.isEmpty()) throw new MlException(HttpStatus.UNPROCESSABLE_CONTENT,
                "EMPTY_IMAGE", "Фотография пуста");
        if (file.getSize() > MAX_BYTES) throw new MlException(HttpStatus.CONTENT_TOO_LARGE,
                "IMAGE_TOO_LARGE", "Размер фотографии превышает 10 МиБ");
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new MlException(HttpStatus.BAD_REQUEST, "INVALID_PHOTO", "Не удалось прочитать фотографию");
        }
        if (bytes.length > MAX_BYTES) throw new MlException(HttpStatus.CONTENT_TOO_LARGE,
                "IMAGE_TOO_LARGE", "Размер фотографии превышает 10 МиБ");
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return new Photo(bytes, "image/jpeg");
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8), PNG))
            return new Photo(bytes, "image/png");
        if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP"))
            return new Photo(bytes, "image/webp");
        throw new MlException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_IMAGE_FORMAT", "Используйте JPEG, PNG или WebP");
    }
}
