package ru.example.prodbackend.taskfolder.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskFolderRequest {

    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "UUID, созданный клиентом для безопасного повтора запроса")
    private UUID id;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 80,
            description = "Название: пробелы по краям удаляются до проверки длины; одинаковые названия допустимы", example = "Документы")
    private String name;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"mint", "cyan", "coral", "sand", "lilac", "forest"},
            description = "Ключ цвета, независимый от темы; RGB и hex не принимаются", example = "mint")
    private String colorKey;
}
