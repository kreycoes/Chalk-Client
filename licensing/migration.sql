CREATE TABLE IF NOT EXISTS license_challenges (
  challenge TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_challenge_expiry ON license_challenges(expires_at);
CREATE TABLE IF NOT EXISTS license_sessions (
  token_hash TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL,
  account_key TEXT NOT NULL,
  valid_until INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_session_expiry ON license_sessions(valid_until);
CREATE TABLE IF NOT EXISTS license_rate (source TEXT PRIMARY KEY, bucket INTEGER NOT NULL, count INTEGER NOT NULL);
CREATE INDEX IF NOT EXISTS idx_rate_bucket ON license_rate(bucket);
