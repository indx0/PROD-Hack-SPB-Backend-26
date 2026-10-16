package ru.example.prodbackend.box.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoxSummaryResponse {

    private long totalBoxes;

    private Map<BoxStatus, Long> countsByStatus;

    private long deferredContentsCount;

    private List<RoomBoxSummaryResponse> rooms;
}
