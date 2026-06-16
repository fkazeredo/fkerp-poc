-- Slice 6: qualification records the main commercial interest (required when the lead is qualified).
ALTER TABLE leads ADD COLUMN main_interest VARCHAR(500);

-- Backfill any leads qualified before this slice so the new constraint is satisfiable.
UPDATE leads SET main_interest = 'Não informado' WHERE status = 'QUALIFIED' AND main_interest IS NULL;

-- Defense in depth (§5.5): a qualified lead must carry its main interest.
ALTER TABLE leads ADD CONSTRAINT chk_leads_qualified_has_interest
    CHECK (status <> 'QUALIFIED' OR main_interest IS NOT NULL);
