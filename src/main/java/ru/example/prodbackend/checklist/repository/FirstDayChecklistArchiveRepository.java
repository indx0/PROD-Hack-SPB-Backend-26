package ru.example.prodbackend.checklist.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.example.prodbackend.checklist.dto.response.ChecklistItemResponse;

@Repository
@RequiredArgsConstructor
public class FirstDayChecklistArchiveRepository {
    private final JdbcTemplate jdbc;

    public Page<ChecklistItemResponse> findAll(UUID projectId, Pageable pageable) {
        long count = jdbc.queryForObject("SELECT count(*) FROM first_day_checklist_archive WHERE project_id = ?", Long.class, projectId);
        var rows = jdbc.query("SELECT * FROM first_day_checklist_archive WHERE project_id = ? ORDER BY created_at, id LIMIT ? OFFSET ?",
                (rs, row) -> ChecklistItemResponse.builder()
                        .id(rs.getObject("id", UUID.class)).projectId(projectId).name(rs.getString("name"))
                        .completed(rs.getTimestamp("completed_at") != null).completedAt(instant(rs, "completed_at"))
                        .createdAt(instant(rs, "created_at")).updatedAt(instant(rs, "updated_at"))
                        .itemId(rs.getObject("item_id", UUID.class)).itemName(rs.getString("item_name"))
                        .boxId(rs.getObject("box_id", UUID.class)).boxNumber(rs.getObject("box_number", Integer.class))
                        .roomId(rs.getObject("room_id", UUID.class)).roomName(rs.getString("room_name"))
                        .roomIconKey(rs.getString("room_icon_key")).build(), projectId, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(rows, pageable, count);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
