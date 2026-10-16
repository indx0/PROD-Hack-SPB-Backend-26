package ru.example.prodbackend.task.dto.response;

import lombok.*;
import ru.example.prodbackend.task.entity.TaskStage;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskTemplateResponse {

    private UUID id;
    private String title;
    private String description;
    private TaskStage stage;
}