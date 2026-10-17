package ru.example.prodbackend.box.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateBoxRequest {

    @Positive
    @Schema(example = "1", description = "Новый номер коробки")
    private Integer number;

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Идентификатор новой комнаты этого же проекта")
    private UUID roomId;

    @Schema(description = "Признак отложенного заполнения содержимого")
    private Boolean contentsDeferred;

    @Size(max = 255)
    @Schema(example = "Упаковать кухню", description = "Подпись до 255 символов; пробелы по краям удаляются, пустая строка очищает подпись; null запрещён")
    private String title;
    @Schema(description = "Хрупкое содержимое; false снимает отметку, null запрещён")
    private Boolean fragile;
    @Schema(description = "Коробка первого дня; false снимает отметку, вещи и чек-лист не меняются; null запрещён")
    private Boolean firstDay;
    @Schema(description = "Везу с собой; false снимает отметку, null запрещён")
    private Boolean carryWithMe;

    @JsonSetter(value = "title", nulls = Nulls.FAIL)
    public void setTitle(String title) { this.title = title; }

    @JsonSetter(value = "fragile", nulls = Nulls.FAIL)
    public void setFragile(Boolean fragile) { this.fragile = fragile; }

    @JsonSetter(value = "firstDay", nulls = Nulls.FAIL)
    public void setFirstDay(Boolean firstDay) { this.firstDay = firstDay; }

    @JsonSetter(value = "carryWithMe", nulls = Nulls.FAIL)
    public void setCarryWithMe(Boolean carryWithMe) { this.carryWithMe = carryWithMe; }

    @JsonSetter(value = "number", nulls = Nulls.FAIL)
    public void setNumber(Integer number) {
        this.number = number;
    }

    @JsonSetter(value = "roomId", nulls = Nulls.FAIL)
    public void setRoomId(UUID roomId) {
        this.roomId = roomId;
    }

    @JsonSetter(value = "contentsDeferred", nulls = Nulls.FAIL)
    public void setContentsDeferred(Boolean contentsDeferred) {
        this.contentsDeferred = contentsDeferred;
    }

    @JsonIgnore
    @AssertTrue(message = "Specify at least one field to update")
    public boolean isUpdatePresent() {
        return number != null || roomId != null || contentsDeferred != null
                || title != null || fragile != null || firstDay != null || carryWithMe != null;
    }

}
