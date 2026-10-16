package ru.example.prodbackend.item.dto;

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
    private BoxStatus status;
    private boolean contentsMatch;
    private boolean sameRoom;
}
