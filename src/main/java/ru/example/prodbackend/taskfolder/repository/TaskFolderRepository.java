package ru.example.prodbackend.taskfolder.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.taskfolder.entity.TaskFolderEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TaskFolderRepository extends JpaRepository<TaskFolderEntity, UUID> {
    Page<TaskFolderEntity> findAllByProject_Id(UUID projectId, Pageable pageable);

    Optional<TaskFolderEntity> findByIdAndProject_Id(UUID folderId, UUID projectId);

    // A UUID may race across different projects; do not abort the transaction on conflict.
    @Modifying
    @Query(value = """
            INSERT INTO task_folders(id, project_id, name, color_key, initial_name, initial_color_key, created_at, updated_at)
            VALUES (:id, :projectId, :name, :colorKey, :name, :colorKey, :now, :now)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("projectId") UUID projectId,
                       @Param("name") String name, @Param("colorKey") String colorKey, @Param("now") Instant now);
}
