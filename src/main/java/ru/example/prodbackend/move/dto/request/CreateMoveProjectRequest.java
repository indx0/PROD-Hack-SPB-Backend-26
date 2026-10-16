package ru.example.prodbackend.move.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMoveProjectRequest {

    @NotBlank(message = "name is blank")
    @Size(max = 255)
    private String name;

    @NotNull(message = "move date is null")
    @Schema(description = "Дата и время переезда", example = "2026-11-01T09:00:00Z")
    private Instant moveDate;

    @Size(max = 1000)
    private String originAddress;

    @Size(max = 1000)
    private String destinationAddress;
}
