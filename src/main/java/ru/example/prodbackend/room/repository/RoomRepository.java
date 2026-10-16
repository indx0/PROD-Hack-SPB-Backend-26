package ru.example.prodbackend.room.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.example.prodbackend.room.entity.RoomEntity;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<RoomEntity, UUID> {

    Page<RoomEntity> findAllByProject_Id(UUID projectId, Pageable pageable);
    Optional<RoomEntity> findByIdAndProject_Id(UUID roomId, UUID projectId);

    boolean existsByProject_IdAndName(UUID projectId, String name);
    boolean existsByProject_IdAndNameAndIdNot(UUID projectId, String name, UUID roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from RoomEntity r
            where r.id = :roomId and r.project.id = :projectId
            """)
    Optional<RoomEntity> findForUpdate(@Param("roomId") UUID roomId, @Param("projectId") UUID projectId);
}