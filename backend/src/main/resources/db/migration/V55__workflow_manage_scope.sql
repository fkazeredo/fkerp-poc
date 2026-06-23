-- Grant the workflow administration scope to every user who already administers the cadastros
-- (reference:manage): managing the configurable workflows (states/transitions/attention rules) is the same
-- back-office administration responsibility. A single neutral scope (not per-domain) keeps it simple — only
-- the administrator persona holds it, and the workflow definitions are a single back-office surface.
INSERT INTO user_scopes (user_id, scope)
SELECT user_id, 'workflow:manage'
FROM user_scopes
WHERE scope = 'reference:manage'
  AND NOT EXISTS (
      SELECT 1 FROM user_scopes existing
      WHERE existing.user_id = user_scopes.user_id AND existing.scope = 'workflow:manage'
  );
