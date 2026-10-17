package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class LinkChecklistItemRequest {
    @NotNull
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "UUID вещи того же проекта")
    private UUID itemId;
}
