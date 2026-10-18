package ru.example.prodbackend.taskfolder.repository;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskFolderCounts {

    private UUID folderId;
    private long taskCount;
    private long completedTaskCount;
}
