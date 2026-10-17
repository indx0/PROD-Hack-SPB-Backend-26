package ru.example.prodbackend.move.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class AddProjectParticipantRequest {
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "UUID существующего пользователя; укажите ровно одно из userId и email")
    private UUID userId;

    @Email
    @Size(min = 1, max = 255)
    @Schema(description = "Точная почта существующего аккаунта; пробелы по краям удаляются", example = "family@example.com")
    private String email;

    public void setEmail(String email) { this.email = email == null ? null : email.strip(); }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Укажите ровно одно поле: userId или email")
    public boolean isExactlyOneSelectorPresent() { return (userId != null) != (email != null); }
}
