package ru.example.prodbackend.room.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class UpdateRoomRequest {

    @Size(min = 1, max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Название не должно состоять только из пробелов")
    @Schema(description = "Новое название; отсутствующее поле или null сохраняет текущее", example = "Спальня")
    private String name;

    @Size(min = 1, max = 64, message = "Ключ значка должен содержать от 1 до 64 символов")
    @Pattern(regexp = "(?Us).*\\S.*", message = "Ключ значка не должен состоять только из пробелов")
    @Setter(AccessLevel.NONE)
    @Schema(types = {"string", "null"}, description = "Отсутствующее поле сохраняет значок; строка меняет его; явный null очищает выбор. Непустая строка до 64 символов; пример ключа клиента — kitchen", example = "kitchen")
    private String iconKey;

    @JsonIgnore
    @Schema(hidden = true)
    @Setter(AccessLevel.NONE)
    private boolean iconKeyPresent;

    @JsonSetter(value = "iconKey", nulls = Nulls.SET)
    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
        this.iconKeyPresent = true;
    }

    public static class UpdateRoomRequestBuilder {
        public UpdateRoomRequestBuilder iconKey(String iconKey) {
            this.iconKey = iconKey;
            this.iconKeyPresent = true;
            return this;
        }
    }
}
