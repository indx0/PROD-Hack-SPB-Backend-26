package ru.example.prodbackend.move.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class RescheduleMoveProjectRequest {
    @NotNull
    @Schema(description = "Новая дата переезда; для сроков задач используется календарная дата UTC", example = "2026-11-15T09:00:00Z")
    private Instant moveDate;
    @NotNull
    @Schema(description = "Явно выбранные невыполненные RELATIVE-задачи; пустой список меняет только дату проекта. Повторы UUID объединяются")
    private List<@NotNull UUID> taskIds;
}
