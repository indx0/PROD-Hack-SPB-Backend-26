--liquibase formatted sql

--changeset indx0:2026-10-17-0005-create-boxes dbms:postgresql
CREATE TABLE boxes (
    id uuid NOT NULL PRIMARY KEY,
    number integer NOT NULL,
    project_id uuid NOT NULL,
    room_id uuid NOT NULL,
    status varchar(255) NOT NULL,
    contents_deferred boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_boxes_project_number UNIQUE (project_id, number),
    CONSTRAINT ck_boxes_positive_number CHECK (number > 0),
    CONSTRAINT ck_boxes_status CHECK (status IN ('PACKING', 'PACKED', 'LOADED', 'DELIVERED', 'UNPACKED')),
    CONSTRAINT fk_boxes_project FOREIGN KEY (project_id) REFERENCES move_projects (id),
    CONSTRAINT fk_boxes_room_project FOREIGN KEY (room_id, project_id) REFERENCES rooms (id, project_id)
);

CREATE INDEX idx_boxes_project_status ON boxes (project_id, status);
CREATE INDEX idx_boxes_room_project ON boxes (room_id, project_id);

--rollback DROP TABLE boxes;
