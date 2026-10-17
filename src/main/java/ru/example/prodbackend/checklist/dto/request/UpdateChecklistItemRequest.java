package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class UpdateChecklistItemRequest {

    @Schema(example = "Посуда", description = "Новое название; отсутствие или null сохраняет текущее значение")
    @Size(max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Название не должно быть пустым")
    private String name;
    @Schema(description = "Выполнение пункта; отсутствие или null сохраняет текущее значение")
    private Boolean completed;
}
