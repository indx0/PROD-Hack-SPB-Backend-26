package ru.example.prodbackend.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import ru.example.prodbackend.item.entity.ItemFlag;
import java.util.List;
import java.util.HashSet;
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
    @NotBlank
    @Size(max = 255)
    private String name;

    @Builder.Default
    @NotNull
    @Size(max = 3)
    private List<@NotNull ItemFlag> flags = List.of();

    @JsonSetter(value = "flags", nulls = Nulls.FAIL)
    public void setFlags(List<ItemFlag> flags) { this.flags = flags; }

    @JsonIgnore
    @AssertTrue(message = "Flags must not contain duplicates")
    public boolean isFlagsUnique() {
        return flags == null || new HashSet<>(flags).size() == flags.size();
    }
}
