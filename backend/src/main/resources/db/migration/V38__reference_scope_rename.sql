-- Neutral reference-data kernel: the cadastro-management scope is no longer CRM-specific (cadastros now
-- exist across CRM/Sales/Booking, all managed through the same neutral scope). Rename the existing grants.
UPDATE user_scopes SET scope = 'reference:manage' WHERE scope = 'crm:reference:manage';
