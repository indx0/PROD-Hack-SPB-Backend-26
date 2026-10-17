package ru.example.prodbackend.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemBoxSuggestionResponse {
    private UUID boxId;
    private int boxNumber;
    private UUID roomId;
    private String roomName;
    @Schema(types = {"string", "null"}, description = "Актуальный ключ значка комнаты; null — стандартное оформление")
    private String roomIconKey;
    private BoxStatus status;
    private boolean contentsMatch;
    private boolean sameRoom;
}
