package ru.example.prodbackend.unit.ml.validation;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import ru.example.prodbackend.ml.exception.MlException;
import ru.example.prodbackend.ml.validation.PhotoValidator;

import static org.assertj.core.api.Assertions.*;

class PhotoValidatorTest {
    private final PhotoValidator validator = new PhotoValidator();

    @Test
    void detectsSupportedSignaturesIgnoringFileMetadata() {
        assertThat(validate(new byte[]{(byte)255, (byte)216, (byte)255}).getMime()).isEqualTo("image/jpeg");
        assertThat(validate(new byte[]{(byte)137,80,78,71,13,10,26,10}).getMime()).isEqualTo("image/png");
        assertThat(validate("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)).getMime()).isEqualTo("image/webp");
    }

    @Test
    void rejectsEmptyOversizedAndUnknownImages() {
        assertCode(new byte[0], "EMPTY_IMAGE");
        assertCode(new byte[PhotoValidator.MAX_BYTES + 1], "IMAGE_TOO_LARGE");
        assertCode(new byte[]{1,2,3}, "UNSUPPORTED_IMAGE_FORMAT");
    }

    @Test
    void acceptsExactlyTenMiB() {
        byte[] bytes = new byte[PhotoValidator.MAX_BYTES];
        bytes[0] = (byte)255; bytes[1] = (byte)216; bytes[2] = (byte)255;
        assertThat(validate(bytes).getBytes()).hasSize(PhotoValidator.MAX_BYTES);
    }

    private PhotoValidator.Photo validate(byte[] bytes) {
        return validator.validate(new MockMultipartFile("photo", "fake.txt", "text/plain", bytes));
    }
    private void assertCode(byte[] bytes, String code) {
        assertThatThrownBy(() -> validate(bytes)).isInstanceOfSatisfying(MlException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
