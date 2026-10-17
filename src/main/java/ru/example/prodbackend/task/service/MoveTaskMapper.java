package ru.example.prodbackend.task.service;

import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.entity.MoveTaskEntity;

public final class MoveTaskMapper {
    private MoveTaskMapper() { }
    public static MoveTaskResponse toResponse(MoveTaskEntity task) {
        return MoveTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .stage(task.getStage())
                .dueDate(task.getDueDate())
                .dueType(task.getDueType()).offsetDays(task.getOffsetDays())
                .assigneeName(task.getAssigneeName())
                .completed(task.getCompletedAt() != null)
                .completedAt(task.getCompletedAt())
                .templateId(task.getTemplate() == null ? null : task.getTemplate().getId())
                .taskSetApplicationId(task.getTaskSetApplication() == null ? null : task.getTaskSetApplication().getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

}
