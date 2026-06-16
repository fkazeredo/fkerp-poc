-- Sprint 2 / Slice 3: opportunity search & filters. Indexes for the new range-filter columns
-- (creation period, expected closing period, estimated value), mirroring idx_leads_created_at (V6).
-- V13 already indexes stage, responsible_person_id and origin_id.
CREATE INDEX idx_opportunities_created_at ON opportunities (created_at);
CREATE INDEX idx_opportunities_expected_close_date ON opportunities (expected_close_date);
CREATE INDEX idx_opportunities_estimated_value ON opportunities (estimated_value);
