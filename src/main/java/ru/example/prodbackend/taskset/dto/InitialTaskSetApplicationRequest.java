package ru.example.prodbackend.taskset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitialTaskSetApplicationRequest {

    @NotNull
    @Schema(description = "UUID собственного активного шаблона; в массиве допускается только один раз")
    private UUID taskSetId;

    @Schema(types = {"string", "null"}, format = "uuid", description = "UUID папки из folders этого же запроса; чужая или не объявленная в запросе папка запрещена. Отсутствие или null — без папки")
    private UUID folderId;
}
