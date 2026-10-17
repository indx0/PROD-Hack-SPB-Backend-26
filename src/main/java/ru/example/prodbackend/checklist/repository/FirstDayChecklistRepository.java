package ru.example.prodbackend.checklist.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.checklist.entity.FirstDayChecklistItemEntity;

import java.util.Optional;
import java.util.UUID;

public interface FirstDayChecklistRepository extends JpaRepository<FirstDayChecklistItemEntity, UUID> {

    @EntityGraph(attributePaths = {"item", "item.box", "item.box.room"})
    Page<FirstDayChecklistItemEntity> findAllByProject_Id(UUID projectId, Pageable pageable);

    boolean existsByItem_IdAndIdNot(UUID itemId, UUID checklistItemId);

    boolean existsByItem_Id(UUID itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FirstDayChecklistItemEntity c where c.item.id = :itemId")
    Optional<FirstDayChecklistItemEntity> findForUpdateByItemId(@Param("itemId") UUID itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from FirstDayChecklistItemEntity i where i.id = :itemId and i.project.id = :projectId")
    Optional<FirstDayChecklistItemEntity> findForUpdate(@Param("itemId") UUID itemId, @Param("projectId") UUID projectId);
}
