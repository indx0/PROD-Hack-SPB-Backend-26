package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.item.entity.ItemFlag;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateItemRequest {

    @Schema(example = "Посуда", description = "Название вещи; пробелы по краям удаляются")
    @NotBlank(message = "name is blank")
    @Size(max = 255, message = "max size is 255")
    private String name;

    @Builder.Default
    @NotNull(message = "quantity is null")
    @Positive(message = "quantity not positive")
    @Schema(example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Количество одинаковых предметов", defaultValue = "1", minimum = "1")
    private Integer quantity = 1;

    @Builder.Default
    @NotNull(message = "first day is null")
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Вещь нужна в первый день", defaultValue = "false")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private Boolean firstDay = false;

    @Builder.Default
    @NotNull
    @Size(max = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(defaultValue = "[]", requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "fragile, first_day, carry_personally без повторов; first_day соответствует firstDay")
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
