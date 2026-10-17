package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateItemRequest {

    @Size(min = 1, max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Item name must not be blank")
    private String name;

    private Boolean lost;

    @Positive
    @Schema(description = "Изменение только в PACKING; новое количество сбрасывает packedAt. Повтор текущего значения сохраняет отметку")
    private Integer quantity;

    private Boolean firstDay;

    @Schema(description = "Отметка упаковки всей записи. Изменяется только в коробке PACKING")
    private Boolean packed;

    @Schema(description = "Отметка распаковки всей записи. Изменяется только в коробке DELIVERED")
    private Boolean unpacked;

    @JsonSetter(value = "name", nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter(value = "lost", nulls = Nulls.FAIL)
    public void setLost(Boolean lost) {
        this.lost = lost;
    }

    @JsonSetter(value = "quantity", nulls = Nulls.FAIL)
    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @JsonSetter(value = "firstDay", nulls = Nulls.FAIL)
    public void setFirstDay(Boolean firstDay) {
        this.firstDay = firstDay;
    }

    @JsonSetter(value = "packed", nulls = Nulls.FAIL)
    public void setPacked(Boolean packed) {
        this.packed = packed;
    }

    @JsonSetter(value = "unpacked", nulls = Nulls.FAIL)
    public void setUnpacked(Boolean unpacked) {
        this.unpacked = unpacked;
    }

    @JsonIgnore
    @AssertTrue(message = "Specify at least one field to update")
    public boolean isUpdatePresent() {
        return name != null
                || lost != null
                || quantity != null
                || firstDay != null
                || packed != null
                || unpacked != null;
    }
}
