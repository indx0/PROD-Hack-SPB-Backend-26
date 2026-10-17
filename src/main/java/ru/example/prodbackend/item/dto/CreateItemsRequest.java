package ru.example.prodbackend.item.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateItemsRequest {

    @Size(min = 1, max = 100, message = "items min size is 1 and max size is 100")
    @Schema(description = "Список названий. Передайте ровно один список: names или items")
    private List<@NotBlank @Size(max = 255) String> names;

    @Size(min = 1, max = 100, message = "items min size is 1 and max size is 100")
    @Schema(description = "Вещи с количеством и отметкой первого дня")
    private List<@NotNull @Valid CreateItemRequest> items;

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Передайте ровно один список: names или items")
    public boolean isExactlyOneListPresent() {
        return (names == null) == (items != null);
    }
}
