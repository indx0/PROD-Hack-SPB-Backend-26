package ru.example.prodbackend.item.repository;

import java.util.UUID;
import lombok.Value;

@Value
public class BoxItemCounts {

    private final UUID boxId;

    private final long totalItems;

    private final long packedItems;

    private final long unpackedItems;
}
