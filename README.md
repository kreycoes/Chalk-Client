# Chalk Client

Open-source Minecraft **1.21.11** client-side addon for **Meteor Client**.
Maintained by Krey. This is an addon, not a standalone Minecraft client.

## Status: 2.0.9 development preview

This repository publishes readable source for inspection and self-building.
The online account-bound licensing implementation is **not yet deployed or tested with a real Minecraft login**. Do not treat this commit as a finished tester release.

The local Beta build compiled with Java 21 / Gradle 9.2.1. Backend tests pass with a simulated Mojang response. Those checks do not establish runtime compatibility, malware-free status, or a match to previously distributed JAR files.

## Features

Chalk includes inventory totem handling, crystal placement helpers, HUD elements, alerts, chunk/entity/block ESP modules, glint customization and other Meteor modules. See `src/main/java/de/example/totemautoinv` for the complete implementation. Module names describe UI features; they do not guarantee information that a server has not sent to the client. Respect the rules of servers you use.

## Build it yourself

Install JDK 21 and Gradle 9.2.1. From this directory:

```powershell
gradle build --no-daemon
```

The default build has no Beta/Lifetime license gate. Other existing variants:

```powershell
gradle build -PbetaBuild --no-daemon
gradle build -PbuyerBuild --no-daemon
gradle build -PadminBuild --no-daemon
```

Output: `build/libs/`. Use the normal remapped JAR, not `-sources.jar`, with the matching Fabric/Meteor installation. Do not install more than one Chalk edition at once.

The source currently uses Loom 1.15.5, Fabric Loader 0.19.3 and Meteor `1.21.11-SNAPSHOT`. That SNAPSHOT is mutable: a later clean build may resolve a different Meteor artifact than the developer's cache. **Bit-for-bit reproducible builds and an exact dependency pin to Meteor build 86 are not established yet.** No Minecraft/Meteor binaries are included.

Optional ProGuard tasks remain in the build for historical Beta/Lifetime packaging. The normal build is not obfuscated; readable sources for those paths are present. There is no claim that obfuscation makes a build trustworthy.

## Transparency and privacy

See [SECURITY.md](SECURITY.md) for networking, account verification, local storage and side effects. Purchaser codes, Cloudflare credentials, player databases, logs and private build history are not in this repository.

The backend source and a fresh schema are under [`licensing/`](licensing/README.md). No production license seeds or private activation records are included.

## License

Code is provided under GNU GPL v3. See [LICENSE](LICENSE) and [THIRD_PARTY.md](THIRD_PARTY.md). You can inspect, modify and build it, including its local license gate; distributing a modified version must comply with the license.
