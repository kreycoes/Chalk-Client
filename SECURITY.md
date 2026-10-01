# Security and privacy notes

Source availability helps independent review; it is not a malware scan, security certification or proof that an unrelated JAR was built from this source.

## Online licensing (development)

Only the Beta and Lifetime editions initialize the online license manager. The default/Admin builds do not require online licensing.

- `OnlineLicense.java` sends an activation code and Minecraft username by HTTPS to the configured `chalk-license-api` Cloudflare Worker (`*.jasonafelt641.workers.dev`). The endpoint is public configuration, not a secret.
- Minecraft's Authlib sends the existing Minecraft access token to Mojang's session service to answer a random challenge. The Worker does **not** receive that access token or the Microsoft password.
- The Worker verifies the Mojang response, stores a hash of the verified UUID, activation/expiry times, code hashes and short-lived session/challenge data. Hashing an account UUID is pseudonymization, not anonymity.
- Cloudflare processes the network IP address. Application rate-limit keys are hashed, minute-bucketed and short lived; Cloudflare's own operational logs are separate.
- Entered activation codes are module settings and may be saved locally by the existing Meteor/Chalk configuration system. Treat the configuration folder as private. Session tokens are held in memory.
- Beta time is 24 consecutive hours from the first successful account activation, including time offline. Re-copying a JAR does not reset the database. Different Minecraft accounts are separate identities.
- The client periodically rechecks access. Failed checks lock Chalk modules; expiry does not delete or damage files. Internet access and a genuine Minecraft login are required for restricted editions.
- No client-side licensing system is tamper-proof. Existing offline JARs are unaffected by the new source.

## Other relevant behavior

- `HomeResetModule` sends configured `/delhome` and `/sethome` commands. Interrupting between them may leave the old home removed. Review its settings before activation.
- Chalk Glint can enable a built-in resource pack and trigger resource reloads.
- Settings/HUD state are written through the existing Meteor/Chalk configuration systems.
- HUD mouse monitoring reads input to draw local indicators; it is not sent to the license service.
- Online Admins includes user-configured default player names; it is not authoritative proof of server permissions.

If you report a problem in a public issue, remove license codes, account tokens, private chat and server addresses from logs first. Never paste account passwords or access tokens into an issue.
