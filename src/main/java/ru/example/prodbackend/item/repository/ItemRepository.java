package ru.example.prodbackend.item.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.example.prodbackend.item.entity.ItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<ItemEntity, UUID> {

    void deleteByBoxId(UUID boxId);

    @EntityGraph(attributePaths = {"box", "box.room", "box.project"})
    Page<ItemEntity> findAllByBoxId(UUID boxId, Pageable pageable);

    @EntityGraph(attributePaths = {"box", "box.room", "box.project"})
    Optional<ItemEntity> findByIdAndBoxId(UUID itemId, UUID boxId);

    @EntityGraph(attributePaths = {"box", "box.room", "box.project"})
    Optional<ItemEntity> findByIdAndBoxProjectIdAndBoxProjectOwnerId(
            UUID itemId, UUID projectId, UUID ownerId
    );

    @EntityGraph(attributePaths = {"box", "box.room", "box.project"})
    @Query(value = """
            select i from ItemEntity i
            where i.box.project.id = :projectId
              and i.box.project.owner.id = :ownerId
              and lower(i.name) like :pattern escape '!'
              and (:lost is null or i.lost = :lost)
            order by lower(i.name), i.id
            """, countQuery = """
            select count(i) from ItemEntity i
            where i.box.project.id = :projectId
              and i.box.project.owner.id = :ownerId
              and lower(i.name) like :pattern escape '!'
              and (:lost is null or i.lost = :lost)
            """)
    Page<ItemEntity> search(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("pattern") String pattern,
            @Param("lost") Boolean lost,
            Pageable pageable
    );

    @Query("""
            select distinct i.box.id from ItemEntity i
            where i.box.project.id = :projectId
              and i.box.project.owner.id = :ownerId
              and lower(i.name) like :pattern escape '!'
            """)
    List<UUID> findMatchingBoxIds(
            @Param("projectId") UUID projectId,
            @Param("ownerId") UUID ownerId,
            @Param("pattern") String pattern
    );
}
