package ru.example.prodbackend.taskset.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(onConstructor_ = @JsonCreator)
public class ApplyTaskSetRequest {
    @Schema(types = {"string", "null"}, format = "uuid", description = "Папка этого переезда для первого применения; null — без папки. Отсутствие поля или тела сохраняет старый сценарий и первоначальное назначение при повторе. Иное явно указанное назначение прежнего применения — 409 TASK_SET_ALREADY_APPLIED")
    private UUID folderId;

    @JsonIgnore
    @Schema(hidden = true)
    private boolean folderIdPresent;

    @JsonSetter(value = "folderId", nulls = Nulls.SET)
    public void setFolderId(UUID folderId) {
        this.folderId = folderId;
        folderIdPresent = true;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Неизвестное поле: " + name);
    }
}
