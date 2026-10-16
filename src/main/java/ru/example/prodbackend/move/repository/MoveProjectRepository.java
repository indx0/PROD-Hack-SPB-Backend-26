package ru.example.prodbackend.move.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

import java.util.Optional;
import java.util.UUID;

public interface MoveProjectRepository extends JpaRepository<MoveProjectEntity, UUID> {

    Page<MoveProjectEntity> findAllByOwner_Id(UUID ownerId, Pageable pageable);
    Optional<MoveProjectEntity> findByIdAndOwner_Id(UUID projectId, UUID ownerId);

    @Modifying
    @Query(value = """
            DELETE FROM items
            WHERE box_id IN (
                SELECT id FROM boxes WHERE project_id = :projectId
            )
            """, nativeQuery = true)
    int deleteProjectItems(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = """
            DELETE FROM boxes WHERE project_id = :projectId
            """, nativeQuery = true)
    int deleteProjectBoxes(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = """
            DELETE FROM rooms WHERE project_id = :projectId
            """, nativeQuery = true)
    int deleteProjectRooms(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = """
            DELETE FROM move_tasks WHERE project_id = :projectId
            """, nativeQuery = true)
    int deleteProjectTasks(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = """
            DELETE FROM first_day_checklist_items
            WHERE project_id = :projectId
            """, nativeQuery = true)
    int deleteProjectChecklist(@Param("projectId") UUID projectId);

    @Modifying
    @Query(value = """
            DELETE FROM project_events WHERE project_id = :projectId
            """, nativeQuery = true)
    int deleteProjectEvents(@Param("projectId") UUID projectId);
}
