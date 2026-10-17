package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.example.prodbackend.item.entity.ItemFlag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.HashSet;
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

    @Schema(description = "false снимает связь с пунктом первого дня, сохраняя пункт; true не создаёт пункт автоматически")
    private Boolean firstDay;

    @Schema(description = "Отметка упаковки всей записи. Изменяется только в коробке PACKING")
    private Boolean packed;

    @Schema(description = "Отметка распаковки всей записи. Изменяется только в коробке DELIVERED")
    private Boolean unpacked;

    @Size(max = 3)
    private List<@NotNull ItemFlag> flags;

    @JsonSetter(value = "flags", nulls = Nulls.FAIL)
    public void setFlags(List<ItemFlag> flags) { this.flags = flags; }

    @JsonIgnore @Schema(hidden = true)
    @AssertTrue(message = "Flags must not contain duplicates")
    public boolean isFlagsUnique() { return flags == null || new HashSet<>(flags).size() == flags.size(); }

    @JsonIgnore @Schema(hidden = true)
    @AssertTrue(message = "firstDay и flags.first_day противоречат друг другу")
    public boolean isFirstDayConsistent() { return flags == null || firstDay == null || firstDay == flags.contains(ItemFlag.FIRST_DAY); }

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
                || unpacked != null || flags != null;
    }
}
