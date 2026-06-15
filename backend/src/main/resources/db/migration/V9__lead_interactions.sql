-- Slice 5: register Lead interactions. Each interaction may schedule the next contact (kept on the
-- row for history; the latest value is also mirrored onto leads.next_contact_at in code).
ALTER TABLE lead_interactions ADD COLUMN next_contact_at TIMESTAMPTZ;

-- Defense in depth (§5.5): every interaction carries a description. All existing rows (creation-time
-- notes) already have content, so the constraint is satisfiable.
ALTER TABLE lead_interactions ADD CONSTRAINT chk_lead_interactions_content
    CHECK (content IS NOT NULL AND btrim(content) <> '');

-- NOTE: result_id stays nullable on purpose. A registered interaction must have a result (enforced at
-- the form, controller DTO, service and entity factory), but the creation-time internal note is not a
-- contact and has no result. There is no DB-level discriminator separating the two paths (the
-- INTERNAL_NOTE type is also usable from the registration endpoint), so the "result required" rule is
-- not expressible as a SQL CHECK and is enforced in layers 1-4 instead (recorded exception, §2/§5.5).
