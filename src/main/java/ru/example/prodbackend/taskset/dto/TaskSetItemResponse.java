package ru.example.prodbackend.taskset.dto;

import ru.example.prodbackend.task.entity.TaskStage;
import java.util.UUID;

public record TaskSetItemResponse(UUID id, String title, String description, TaskStage stage, Integer offsetDays, int position) { }
