package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class MoveItemRequest {
    @NotNull
    @Schema(description = "UUID целевой коробки того же проекта")
    private UUID targetBoxId;
}
