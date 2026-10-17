package ru.example.prodbackend.move.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class AddProjectParticipantRequest {
    @Schema(description = "UUID существующего пользователя; укажите ровно одно из userId и email")
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
