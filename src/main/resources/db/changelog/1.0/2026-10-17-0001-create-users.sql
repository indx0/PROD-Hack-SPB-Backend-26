--liquibase formatted sql

--changeset indx0:2026-10-17-0001-create-users dbms:postgresql
CREATE TABLE users (
    id uuid NOT NULL PRIMARY KEY,
    email varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    role varchar(255) NOT NULL,
    first_name varchar(255),
    last_name varchar(255),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

--rollback DROP TABLE users;
