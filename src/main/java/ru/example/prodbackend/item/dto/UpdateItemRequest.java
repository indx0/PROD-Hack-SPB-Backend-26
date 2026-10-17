package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import ru.example.prodbackend.item.entity.ItemFlag;
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

    @Size(max = 3)
    private List<@NotNull ItemFlag> flags;

    @JsonSetter(value = "flags", nulls = Nulls.FAIL)
    public void setFlags(List<ItemFlag> flags) { this.flags = flags; }

    @JsonIgnore
    @AssertTrue(message = "Flags must not contain duplicates")
    public boolean isFlagsUnique() {
        return flags == null || new HashSet<>(flags).size() == flags.size();
    }

    @JsonSetter(value = "name", nulls = Nulls.FAIL)
    public void setName(String name) {
        this.name = name;
    }

    @JsonSetter(value = "lost", nulls = Nulls.FAIL)
    public void setLost(Boolean lost) {
        this.lost = lost;
    }

    @JsonIgnore
    @AssertTrue(message = "Specify at least one field to update")
    public boolean isUpdatePresent() {
        return name != null || lost != null || flags != null;
    }
}
