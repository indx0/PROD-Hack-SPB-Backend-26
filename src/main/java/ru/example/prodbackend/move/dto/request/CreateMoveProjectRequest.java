package ru.example.prodbackend.move.dto.request;

import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class CreateMoveProjectRequest {

    @Schema(example = "Посуда", description = "Название проекта переезда")
    @NotBlank(message = "name is blank")
    @Size(max = 255)
    private String name;

    @NotNull(message = "move date is null")
    @Schema(description = "Дата и время переезда", example = "2026-11-01T09:00:00Z")
    private Instant moveDate;

    @Schema(example = "Москва, ул. Примерная, д. 1", description = "Адрес отправления; необязательное поле")
    @Size(max = 1000)
    private String originAddress;

    @Schema(example = "Москва, ул. Новая, д. 2", description = "Адрес назначения; необязательное поле")
    @Size(max = 1000)
    private String destinationAddress;

    @Builder.Default
    @NotNull
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, description = "Создать четыре стандартных пункта первого дня. false — пустой чек-лист; отсутствие поля — true для совместимости", defaultValue = "true")
    private Boolean includeFirstDayChecklist = true;

    @Builder.Default
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Добровольный выбор собственных наборов: без поля или [] проект создаётся без задач. Повторные UUID объединяются; недоступный набор откатывает всё создание")
    private List<@NotNull UUID> taskSetIds = List.of();

    @Builder.Default
    @Valid
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Необязательный список участников: каждый задаётся по userId или email. Без подтверждения, с равными правами. По умолчанию пустой список")
    private List<@NotNull AddProjectParticipantRequest> participants = List.of();
}
