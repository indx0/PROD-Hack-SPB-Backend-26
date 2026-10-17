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
import ru.example.prodbackend.taskfolder.dto.CreateTaskFolderRequest;
import ru.example.prodbackend.taskset.dto.InitialTaskSetApplicationRequest;

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
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, deprecated = true, description = "Совместимость со старым клиентом: явный true создаёт четыре обычные задачи BEFORE_MOVE без срока в отдельной обычной папке «Личные задачи», mint. Legacy-записи не создаются. Отсутствие/false ничего не добавляет; null запрещён. Новый клиент всегда отправляет false", defaultValue = "false")
    private Boolean includeFirstDayChecklist = false;

    @Builder.Default
    @Valid
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Необязательные папки нового переезда, по умолчанию []. Максимум 100; UUID уникальны в запросе, имя и цвет независимы от шаблонов. Пустые папки допустимы. Создаются атомарно со всем планом")
    private List<@NotNull CreateTaskFolderRequest> folders = List.of();

    @Builder.Default
    @Valid
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Необязательные применения собственных шаблонов, по умолчанию []. Максимум 100; повтор taskSetId — 422. Ненулевой folderId должен присутствовать в folders этого запроса. Нельзя одновременно с непустым taskSetIds. Ошибка любого применения откатывает весь проект")
    private List<@NotNull InitialTaskSetApplicationRequest> taskSetApplications = List.of();

    @Builder.Default
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(deprecated = true, description = "Совместимый старый выбор собственных наборов без папки, максимум 100 UUID; повторы объединяются. Нельзя одновременно с непустым taskSetApplications — 422. Отсутствие или [] ничего не добавляет; недоступный набор откатывает всё создание")
    private List<@NotNull UUID> taskSetIds = List.of();

    @Builder.Default
    @Valid
    @Size(max = 100)
    @JsonSetter(nulls = Nulls.FAIL)
    @Schema(description = "Необязательный список участников: каждый задаётся по userId или email. Без подтверждения, с равными правами. По умолчанию пустой список")
    private List<@NotNull AddProjectParticipantRequest> participants = List.of();
}
