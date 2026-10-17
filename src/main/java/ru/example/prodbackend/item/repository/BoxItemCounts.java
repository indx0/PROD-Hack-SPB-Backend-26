package ru.example.prodbackend.item.repository;

import java.util.UUID;

public record BoxItemCounts(UUID boxId, long totalItems, long packedItems, long unpackedItems) {
}
