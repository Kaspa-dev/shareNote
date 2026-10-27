-- Preserve existing standalone groups and their notes before enforcing membership.
DO $$
DECLARE
    imported_organization_id BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM groups WHERE organization_id IS NULL) THEN
        INSERT INTO organizations(name, description)
        VALUES ('Imported groups', 'Previously standalone groups. Move these groups to the appropriate organization as needed.')
        RETURNING id INTO imported_organization_id;

        UPDATE groups SET organization_id = imported_organization_id
        WHERE organization_id IS NULL;
    END IF;
END $$;

ALTER TABLE groups ALTER COLUMN organization_id SET NOT NULL;
