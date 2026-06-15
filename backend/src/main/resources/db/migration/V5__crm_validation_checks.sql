-- Defense in depth (layer 5): DB CHECK constraints mirroring the domain / Bean-Validation rules,
-- so the database is the last guard even if a future code path bypasses the upper layers.

-- Reference-data sort order is non-negative.
ALTER TABLE origins             ADD CONSTRAINT chk_origins_sort_order             CHECK (sort_order >= 0);
ALTER TABLE loss_reasons        ADD CONSTRAINT chk_loss_reasons_sort_order        CHECK (sort_order >= 0);
ALTER TABLE interaction_types   ADD CONSTRAINT chk_interaction_types_sort_order   CHECK (sort_order >= 0);
ALTER TABLE interaction_results ADD CONSTRAINT chk_interaction_results_sort_order CHECK (sort_order >= 0);

-- A lead must carry at least one contact channel; phone/WhatsApp are digits only when present.
ALTER TABLE leads ADD CONSTRAINT chk_leads_at_least_one_contact
    CHECK (phone IS NOT NULL OR whatsapp IS NOT NULL OR email IS NOT NULL);
ALTER TABLE leads ADD CONSTRAINT chk_leads_phone_digits
    CHECK (phone IS NULL OR phone ~ '^[0-9]+$');
ALTER TABLE leads ADD CONSTRAINT chk_leads_whatsapp_digits
    CHECK (whatsapp IS NULL OR whatsapp ~ '^[0-9]+$');
ALTER TABLE leads ADD CONSTRAINT chk_leads_status
    CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'LOST'));
