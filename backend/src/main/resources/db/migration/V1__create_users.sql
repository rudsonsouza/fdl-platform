CREATE TABLE users (
    id            TEXT NOT NULL PRIMARY KEY,
    name          TEXT NOT NULL,
    -- NOCASE: a unicidade ignora maiúsculas/minúsculas, alinhada à normalização do cadastro.
    email         TEXT NOT NULL COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    created_at    TEXT NOT NULL,
    updated_at    TEXT NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);
