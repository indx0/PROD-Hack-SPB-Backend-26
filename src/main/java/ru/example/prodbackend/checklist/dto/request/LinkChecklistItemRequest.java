package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class LinkChecklistItemRequest {
    @NotNull
    @Schema(description = "UUID вещи того же проекта")
    private UUID itemId;
}
