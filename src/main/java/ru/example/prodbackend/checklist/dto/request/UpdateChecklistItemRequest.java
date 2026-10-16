package ru.example.prodbackend.checklist.dto.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class UpdateChecklistItemRequest {

    @Size(max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "Название не должно быть пустым")
    private String name;

    private Boolean completed;
}
