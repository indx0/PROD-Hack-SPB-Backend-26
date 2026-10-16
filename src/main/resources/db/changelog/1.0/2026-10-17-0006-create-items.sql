--liquibase formatted sql

--changeset indx0:2026-10-17-0006-create-items dbms:postgresql
CREATE TABLE items (
    id uuid NOT NULL PRIMARY KEY,
    box_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    lost boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_items_box FOREIGN KEY (box_id) REFERENCES boxes (id)
);

CREATE INDEX idx_items_box_created_id ON items (box_id, created_at, id);
CREATE INDEX idx_items_name ON items (name);

--rollback DROP TABLE items;
