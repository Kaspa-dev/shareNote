CREATE TABLE organizations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(2000) NOT NULL
);
CREATE TABLE groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    description VARCHAR(2000) NOT NULL,
    organization_id BIGINT REFERENCES organizations(id) ON DELETE RESTRICT
);
CREATE TABLE notes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    content TEXT NOT NULL CHECK (length(trim(content)) > 0 AND length(content) <= 20000),
    category VARCHAR(100) NOT NULL CHECK (length(trim(category)) > 0),
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE RESTRICT
);
CREATE INDEX groups_organization_idx ON groups(organization_id);
CREATE INDEX notes_group_idx ON notes(group_id);
