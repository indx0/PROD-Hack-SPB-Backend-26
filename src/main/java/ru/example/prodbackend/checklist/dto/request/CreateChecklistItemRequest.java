package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateChecklistItemRequest {

    @Schema(example = "Посуда", description = "Название пункта чек-листа")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Необязательная вещь этого проекта. Привязка устанавливает firstDay=true")
    private UUID itemId;
}
