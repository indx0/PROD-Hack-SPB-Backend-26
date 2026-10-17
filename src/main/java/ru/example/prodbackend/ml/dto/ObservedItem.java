package ru.example.prodbackend.ml.dto;

import java.util.UUID;

/** A visible physical object; null itemId means no confident inventory match. */
public record ObservedItem(UUID itemId, String photoDescription) { }
