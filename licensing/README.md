# Licensing backend (development, not production-validated)

Cloudflare Worker + D1, binding name `DB`.

For a new database, apply `schema.sql`, then `migration.sql`. Store your own normalized-code SHA-256 hashes in `licenses`. No real codes, seed data, database identifiers or Cloudflare credentials are included. Never put those into a public commit.

`worker.mjs` provides `/health`, `/challenge`, `/activate` and `/check`. Challenges are random, one-use and time limited. Only a Mojang-verified UUID determines the account binding. Activation records preserve the first start time. Lifetime uses an atomic conditional insert to limit account claims. Requests are rate limited.

Local tests with Node.js 24:

```sh
node licensing/worker.test.mjs
```

Tests use in-memory SQLite and mock Mojang; they do not activate a real Minecraft account. A passing result is not an end-to-end deployment test. Before distribution, test live authentication, restart/reinstall behavior, expiry, concurrency and network failure recovery.

All test codes are fictitious. Public clients can be modified; do not claim unbreakable copy protection.
