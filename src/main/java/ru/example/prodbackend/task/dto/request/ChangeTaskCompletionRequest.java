package ru.example.prodbackend.task.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class ChangeTaskCompletionRequest {

    @NotNull
    @Schema(description = "true — выполнить, false — вернуть в работу")
    private Boolean completed;
}