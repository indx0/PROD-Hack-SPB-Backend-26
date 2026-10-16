--liquibase formatted sql

--changeset indx0:2026-10-17-0002-create-refresh_tokens dbms:postgresql
CREATE TABLE refresh_tokens (
    id uuid NOT NULL PRIMARY KEY,
    expiry_at timestamp with time zone NOT NULL,
    refresh_token varchar(64) NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (refresh_token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

--rollback DROP TABLE refresh_tokens;
