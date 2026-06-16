-- Lead deduplication (last-resort guard). Among OPEN (non-lost) leads, the phone and the e-mail must
-- be unique. The application service rejects duplicates first with a friendly 409 (lead.duplicate),
-- matching phone/WhatsApp numbers interchangeably; these partial unique indexes catch races and are
-- translated to a 409 by the global handler (no SQL/constraint name leaks). A Lost lead is excluded,
-- so the same contact can be registered again after a previous lead was lost.
CREATE UNIQUE INDEX uq_leads_phone_open ON leads (phone) WHERE status <> 'LOST' AND phone IS NOT NULL;
CREATE UNIQUE INDEX uq_leads_email_open ON leads (LOWER(email)) WHERE status <> 'LOST' AND email IS NOT NULL;
