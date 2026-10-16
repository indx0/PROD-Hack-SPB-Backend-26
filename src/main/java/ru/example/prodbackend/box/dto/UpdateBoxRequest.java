package ru.example.prodbackend.box.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class UpdateBoxRequest {

    @Positive
    @Schema(description = "Новый номер коробки")
    private Integer number;

    @Schema(description = "Идентификатор новой комнаты этого же проекта")
    private UUID roomId;

    @Schema(description = "Признак отложенного заполнения содержимого")
    private Boolean contentsDeferred;

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
        return number != null
                || roomId != null
                || contentsDeferred != null;
    }

}
