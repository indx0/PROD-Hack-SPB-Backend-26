package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.*;
import jakarta.validation.constraints.AssertTrue;
import ru.example.prodbackend.item.entity.ItemFlag;
import java.util.List;
import java.util.HashSet;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateItemRequest {

    @NotBlank(message = "name is blank")
    @Size(max = 255, message = "max size is 255")
    private String name;

    @Builder.Default
    @NotNull(message = "quantity is null")
    @Positive(message = "quantity not positive")
    @Schema(description = "Количество одинаковых предметов", defaultValue = "1", minimum = "1")
    private Integer quantity = 1;

    @Builder.Default
    @NotNull(message = "first day is null")
    @Schema(description = "Вещь нужна в первый день", defaultValue = "false")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private Boolean firstDay = false;

    @Builder.Default
    @NotNull
    @Size(max = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "fragile, first_day, carry_personally без повторов; first_day соответствует firstDay")
    private List<@NotNull ItemFlag> flags = List.of();

    @JsonIgnore @Schema(hidden = true)
    private boolean firstDayPresent;
    @JsonIgnore @Schema(hidden = true)
    private boolean flagsPresent;

    public void setFirstDay(Boolean firstDay) { this.firstDay = firstDay; this.firstDayPresent = true; }
    @JsonSetter(value = "flags", nulls = Nulls.FAIL)
    public void setFlags(List<ItemFlag> flags) { this.flags = flags; this.flagsPresent = true; }

    @JsonIgnore @Schema(hidden = true)
    @AssertTrue(message = "Flags must not contain duplicates")
    public boolean isFlagsUnique() { return flags == null || new HashSet<>(flags).size() == flags.size(); }

    @JsonIgnore @Schema(hidden = true)
    @AssertTrue(message = "firstDay и flags.first_day противоречат друг другу")
    public boolean isFirstDayConsistent() {
        return flags == null || firstDay == null || !(flagsPresent || !flags.isEmpty())
                || !(firstDayPresent || Boolean.TRUE.equals(firstDay)) || firstDay == flags.contains(ItemFlag.FIRST_DAY);
    }
}
