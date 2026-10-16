package ru.example.prodbackend.box.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateBoxRequest {

    @NotNull
    @Positive
    @Schema(description = "Положительный номер, уникальный внутри проекта")
    private Integer number;

    @NotNull
    @Schema(description = "Идентификатор комнаты этого же проекта")
    private UUID roomId;

    @Schema(description = "Отложить заполнение содержимого", defaultValue = "false")
    private boolean contentsDeferred;
}
