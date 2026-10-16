--liquibase formatted sql

--changeset indx0:2026-10-17-0003-create-move_projects dbms:postgresql
CREATE TABLE move_projects (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    move_date timestamp with time zone NOT NULL,
    origin_address varchar(1000),
    destination_address varchar(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_move_projects_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_move_projects_owner ON move_projects (owner_id);

--rollback DROP TABLE move_projects;
