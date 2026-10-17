package ru.example.prodbackend.ml.dto;

import java.util.UUID;

/** Immutable entry from the inventory snapshot, also used in the LLM request. */
public record InventoryItem(UUID itemId, String name) { }
