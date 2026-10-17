package ru.example.prodbackend.box.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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

    @Builder.Default
    @Size(max = 255)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Подпись до 255 символов, пробелы по краям удаляются; по умолчанию пустая строка", example = "Посуда для кухни")
    private String title = "";

    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Хрупкое содержимое", defaultValue = "false")
    private boolean fragile;

    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Коробка нужна в первый день; не меняет вещи и чек-лист", defaultValue = "false")
    private boolean firstDay;

    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Пользователь перевозит коробку с собой; не меняет статусы", defaultValue = "false")
    private boolean carryWithMe;
}
