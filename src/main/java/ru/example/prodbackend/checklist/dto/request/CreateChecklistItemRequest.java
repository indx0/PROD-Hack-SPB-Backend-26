package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateChecklistItemRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Schema(description = "Необязательная вещь этого проекта. Привязка устанавливает firstDay=true")
    private UUID itemId;
}
