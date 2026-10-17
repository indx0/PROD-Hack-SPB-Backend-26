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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BoxRepository extends JpaRepository<BoxEntity, UUID> {

    boolean existsByRoom_Id(UUID roomId);

    @EntityGraph(attributePaths = "room")
    Optional<BoxEntity> findByIdAndProject_Id(UUID boxId, UUID projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b from BoxEntity b
            where b.id = :boxId and b.project.id = :projectId
            """)
    Optional<BoxEntity> findForUpdate(@Param("boxId") UUID boxId, @Param("projectId") UUID projectId);

    boolean existsByProject_IdAndNumber(
            UUID projectId,
            int number
    );

    boolean existsByProject_IdAndNumberAndIdNot(UUID projectId, int number, UUID boxId);

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
                      and (:fragile is null or b.fragile = :fragile)
                      and (:firstDay is null or b.firstDay = :firstDay)
                      and (:carryWithMe is null or b.carryWithMe = :carryWithMe)
                    """,
            countQuery = """
                    select count(b)
                    from BoxEntity b
                    where b.project.id = :projectId
                      and (:number is null or b.number = :number)
                      and (:roomId is null or b.room.id = :roomId)
                      and (:status is null or b.status = :status)
                      and (:contentsDeferred is null or b.contentsDeferred = :contentsDeferred)
                      and (:fragile is null or b.fragile = :fragile)
                      and (:firstDay is null or b.firstDay = :firstDay)
                      and (:carryWithMe is null or b.carryWithMe = :carryWithMe)
                    """
    )
    Page<BoxEntity> findAllByFilters(
            @Param("projectId") UUID projectId,
            @Param("number") Integer number,
            @Param("roomId") UUID roomId,
            @Param("status") BoxStatus status,
            @Param("contentsDeferred") Boolean contentsDeferred,
            @Param("fragile") Boolean fragile,
            @Param("firstDay") Boolean firstDay,
            @Param("carryWithMe") Boolean carryWithMe,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "room")
    List<BoxEntity> findAllByProject_IdOrderByNumberAscIdAsc(UUID projectId);

    @EntityGraph(attributePaths = "room")
    @Query("""
            select b from BoxEntity b
            where b.project.id = :projectId and b.project.owner.id = :ownerId
              and b.status <> BoxStatus.UNPACKED
              and (b.id in :matchingBoxIds or b.room.id = :roomId)
            order by case when b.id in :matchingBoxIds then 1 else 0 end desc,
                     case when b.room.id = :roomId then 1 else 0 end desc,
                     b.number, b.id
            """)
    List<BoxEntity> findSuggestions(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("roomId") UUID roomId,
            @Param("matchingBoxIds") Collection<UUID> matchingBoxIds,
            Pageable pageable
    );
}
