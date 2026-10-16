package ru.example.prodbackend.box.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomBoxSummaryResponse {

    private UUID roomId;

    private String roomName;

    private long totalBoxes;

    private Map<BoxStatus, Long> countsByStatus;

    private long unpackedBoxes;

    private int unpackingProgressPercent;
}
