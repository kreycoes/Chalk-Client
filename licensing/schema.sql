CREATE TABLE IF NOT EXISTS licenses (
 code_hash TEXT PRIMARY KEY,
 edition TEXT NOT NULL CHECK (edition IN ('lifetime','beta')),
 active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0,1)),
 max_accounts INTEGER,
 created_at INTEGER NOT NULL DEFAULT (unixepoch()*1000)
);
CREATE TABLE IF NOT EXISTS license_activations (
 code_hash TEXT NOT NULL,
 account_key TEXT NOT NULL,
 activated_at INTEGER NOT NULL,
 expires_at INTEGER,
 last_seen_at INTEGER NOT NULL,
 PRIMARY KEY(code_hash, account_key),
 FOREIGN KEY(code_hash) REFERENCES licenses(code_hash) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_license_activations_account ON license_activations(account_key);
