package ru.example.prodbackend.box.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.example.prodbackend.box.entity.BoxEntity;
import ru.example.prodbackend.box.entity.BoxStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoxRepository extends JpaRepository<BoxEntity, UUID> {
    @EntityGraph(attributePaths = "room")
    Optional<BoxEntity> findByIdAndProjectId(
            UUID boxId,
            UUID projectId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from BoxEntity b
            where b.id = :boxId and b.project.id = :projectId
            """)
    Optional<BoxEntity> findForUpdate(
            @Param("boxId") UUID boxId,
            @Param("projectId") UUID projectId
    );

    boolean existsByProjectIdAndNumber(
            UUID projectId,
            int number
    );

    boolean existsByProjectIdAndNumberAndIdNot(
            UUID projectId,
            int number,
            UUID boxId
    );

    @EntityGraph(attributePaths = "room")
    @Query(
            value = """
                    select b
                    from BoxEntity b
                    where b.project.id = :projectId
                      and (:number is null or b.number = :number)
                      and (:roomId is null or b.room.id = :roomId)
                      and (:status is null or b.status = :status)
                      and (:contentsDeferred is null or b.contentsDeferred = :contentsDeferred)
                    """,
            countQuery = """
                    select count(b)
                    from BoxEntity b
                    where b.project.id = :projectId
                      and (:number is null or b.number = :number)
                      and (:roomId is null or b.room.id = :roomId)
                      and (:status is null or b.status = :status)
                      and (:contentsDeferred is null or b.contentsDeferred = :contentsDeferred)
                    """
    )
    Page<BoxEntity> findAllByFilters(
            @Param("projectId") UUID projectId,
            @Param("number") Integer number,
            @Param("roomId") UUID roomId,
            @Param("status") BoxStatus status,
            @Param("contentsDeferred") Boolean contentsDeferred,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "room")
    List<BoxEntity> findAllByProjectIdOrderByNumberAscIdAsc(
            UUID projectId
    );
}
