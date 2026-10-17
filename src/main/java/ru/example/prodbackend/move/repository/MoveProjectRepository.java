package ru.example.prodbackend.move.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.move.entity.MoveProjectEntity;

import java.util.Optional;
import java.util.UUID;

public interface MoveProjectRepository extends JpaRepository<MoveProjectEntity, UUID> {

    Page<MoveProjectEntity> findAllByOwner_Id(UUID ownerId, Pageable pageable);
    Optional<MoveProjectEntity> findByIdAndOwner_Id(UUID projectId, UUID ownerId);
    boolean existsByOwner_IdAndName(UUID ownerId, String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MoveProjectEntity p where p.id = :projectId and p.owner.id = :ownerId")
    Optional<MoveProjectEntity> findForUpdate(@Param("projectId") UUID projectId, @Param("ownerId") UUID ownerId);

    @Query(value = """
            select p from MoveProjectEntity p where p.owner.id = :ownerId
            and (:archived is null or (:archived = true and p.archivedAt is not null)
                or (:archived = false and p.archivedAt is null))
            """, countQuery = """
            select count(p) from MoveProjectEntity p where p.owner.id = :ownerId
            and (:archived is null or (:archived = true and p.archivedAt is not null)
                or (:archived = false and p.archivedAt is null))
            """)
    Page<MoveProjectEntity> findAllByArchiveFilter(@Param("ownerId") UUID ownerId, @Param("archived") Boolean archived, Pageable pageable);

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
