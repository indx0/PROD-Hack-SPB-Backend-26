package ru.example.prodbackend.taskfolder.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class UpdateTaskFolderRequest {
    @Schema(minLength = 1, maxLength = 80, description = "Новое название после удаления пробелов по краям. Отсутствие сохраняет значение; null запрещён")
    private String name;

    @Schema(allowableValues = {"mint", "cyan", "coral", "sand", "lilac", "forest"},
            description = "Новый ключ цвета. Отсутствие сохраняет значение; null запрещён")
    private String colorKey;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean namePresent;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean colorKeyPresent;

    @JsonSetter(value = "name", nulls = Nulls.SET)
    public void setName(String name) {
        this.name = name;
        namePresent = true;
    }

    @JsonSetter(value = "colorKey", nulls = Nulls.SET)
    public void setColorKey(String colorKey) {
        this.colorKey = colorKey;
        colorKeyPresent = true;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Неизвестное поле: " + name);
    }
}
