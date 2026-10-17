package ru.example.prodbackend.taskfolder.repository;

import java.util.UUID;

public record TaskFolderCounts(UUID folderId, long taskCount, long completedTaskCount) { }
