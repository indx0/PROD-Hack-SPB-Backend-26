package ru.example.prodbackend.task.service;

import ru.example.prodbackend.task.dto.response.MoveTaskResponse;
import ru.example.prodbackend.task.entity.MoveTaskEntity;
import ru.example.prodbackend.user.entity.UserEntity;
import java.util.stream.Stream;
import java.util.stream.Collectors;

public final class MoveTaskMapper {
    private MoveTaskMapper() { }
    public static MoveTaskResponse toResponse(MoveTaskEntity task) {
        var response = MoveTaskResponse.builder()
                .id(task.getId())
                .projectId(task.getProject().getId())
                .folderId(task.getFolder() == null ? null : task.getFolder().getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .stage(task.getStage())
                .dueDate(task.getDueDate())
                .dueType(task.getDueType()).offsetDays(task.getOffsetDays())
                .assigneeId(task.getAssignee() == null ? null : task.getAssignee().getId())
                .assigneeName(task.getAssignee() == null ? task.getAssigneeName() : displayName(task.getAssignee()))
                .completed(task.getCompletedAt() != null)
                .completedAt(task.getCompletedAt())
                .templateId(task.getTemplate() == null ? null : task.getTemplate().getId())
                .taskSetApplicationId(task.getTaskSetApplication() == null ? null : task.getTaskSetApplication().getId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt());
        if (task.getLinkedItem() != null) {
            var item = task.getLinkedItem();
            var box = item.getBox();
            var room = box.getRoom();
            response.linkedItemId(item.getId()).linkedItemName(item.getName())
                    .boxId(box.getId()).boxNumber(box.getNumber())
                    .roomId(room.getId()).roomName(room.getName()).roomIconKey(room.getIconKey());
        }
        return response.build();
    }

    private static String displayName(UserEntity user) {
        var name = Stream.of(user.getFirstName(), user.getLastName()).filter(value -> value != null && !value.isBlank())
                .map(String::strip).collect(Collectors.joining(" "));
        return name.isEmpty() ? user.getEmail() : name;
    }
}
