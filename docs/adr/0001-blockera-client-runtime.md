# ADR 0001: Blockera Client Runtime

- Status: MVP implemented
- Date: 2026-07-18

## Context

Blockera Launcher is a Modrinth App/Theseus fork. Profiles are stored by
`packages/app-lib/src/state/profiles.rs`; catalog packs are described in
`packages/app-lib/resources/modpacks.json`; MRPACK files are downloaded,
validated and installed by `packages/app-lib/src/api/pack/catalog_install.rs`
and `install_mrpack.rs`; Minecraft is installed and launched by
`packages/app-lib/src/launcher/mod.rs`.

The public `blockera.space` site currently serves a static launcher landing
page. The probes performed for this ADR found no public runtime manifest, HTTP
API, WebSocket API or resolvable `api.blockera.space` host. The client must not
invent credentials or silently trust an unverified JAR while those services are
being designed.

## Decision

Only catalog profiles carrying `Profile.external_pack` are Blockera profiles in
the MVP. User-created and imported profiles, including vanilla profiles, remain
unchanged. This boundary avoids trying to load a mod into vanilla Minecraft.

Every catalog entry must contain a `blockeraCore` object with:

```json
{
	"id": "blockera-core",
	"version": "0.4.0",
	"fileName": "blockera-core-0.4.0.jar",
	"url": "https://github.com/dw1rf/BlockEra-Launcher/releases/download/blockera-core-v0.4.0/blockera-core-0.4.0.jar",
	"sha256": "01f33efc807ba555aa1966e8ed997806b19bac9a9146a21cedffde81931a1f0b",
	"size": 981433,
	"required": true,
	"firstParty": true
}
```

On catalog installation the launcher writes a profile-local immutable snapshot
to `.blockera/runtime-manifest.json`. Before installation completes and before
every later launch it:

1. refreshes the snapshot only from the validated Blockera catalog and only
   when catalog ID, pack SHA-256, Minecraft version and Forge version still
   match the installed profile;
2. verifies `mods/blockera-core-<version>.jar` using SHA-256;
3. installs the core from the launcher-bundled first-party artifact for the
   bundled version, or downloads a newer version from its allowlisted URL;
4. verifies length and SHA-256 before replacing through a staged `.part` file;
5. removes only older files matching the reserved
   `blockera-core-*.jar` namespace;
6. aborts profile installation or game launch if the required core cannot be
   verified.

The bundled artifact is built from `blockera-core/` as part of the Rust crate's
build script. It is not a third-party binary and keeps a newly installed
launcher usable before the matching GitHub release asset is published. Remote
updates remain manifest-driven and checksum-verified.

No addon discovery, reflective class loading or addon store is included. The
widget registry accepts only the `blockera:` namespace.

## Core module boundary

The Forge 1.19.2 MVP is intentionally small:

- `api/`: `Widget`, first-party-only `WidgetRegistry`;
- `ui/`: theme-independent `ThemeTokens`, shared drawing primitives, a full
  `TitleScreen` replacement, Control Center, coming-soon screen and HUD editor;
- `hud/`: `HudWidget`, nine `HudAnchor` values, atomic `HudLayoutStore` and ten
  local-data widgets (FPS, coordinates, direction, biome, speed, memory,
  clock/world time, ping, player count and active effects);
- `integrations/`: JDK HTTP/WebSocket transport shell;
- `config/`: HTTPS/WSS endpoint configuration from JVM system properties.

The Forge client replaces only the exact vanilla `TitleScreen`; passing
`-Dblockera.disableCustomTitleScreen=true` is an emergency escape hatch. The
Blockera screen keeps the vanilla panorama but uses a local logo and Blockera
controls. Unavailable online sections show an explicit coming-soon explanation.

The HUD registry accepts only `blockera:*` identifiers. Layouts live in
`config/blockera-core/hud-layouts.json`, are written through a temporary file,
and use anchors plus offsets so resolution changes preserve placement. Invalid
JSON is backed up before safe defaults are loaded. Forge config contains only
the global title-screen, Fancy theme and HUD switches. `Right Shift` opens the
Control Center. No server values are fabricated; ping and online display an
em dash outside a multiplayer connection.

## Planned API contract

The following contract is a TODO, not a claim that endpoints already exist:

- `GET /api/v1/client/bootstrap`: current player summary, feature flags and
  widget availability;
- `GET /api/v1/client/online`: online count and optional server breakdown;
- `GET /api/v1/client/balance`: authenticated display balance;
- `GET /api/v1/client/events`: active and upcoming events;
- `GET /api/v1/client/quests`: authenticated quest summaries;
- `GET /api/v1/client/notifications`: server notifications with stable IDs;
- `WSS /api/v1/client/events`: versioned events for online, balance, events,
  quests and notifications.

Responses must carry a schema version and server time. The backend should issue
short-lived player-scoped tokens after an authenticated launcher/server flow.
No service secret, signing key or long-lived token may be compiled into the
launcher or mod. The Java client accepts a runtime token supplier and does not
persist tokens. Public endpoints are passed through the profile runtime manifest
as `integrations.apiBaseUrl` and `integrations.websocketUrl`; until real HTTPS
and WSS services exist both values remain `null`.

## Releasing a new core version

1. Change `version` in `blockera-core/build.gradle` and
   `BUNDLED_CORE_VERSION` in `blockera_runtime.rs`.
2. Build `blockera-core.jar` and record its byte length and SHA-256.
3. Publish the same bytes as the first-party GitHub release asset
   `blockera-core-<version>.jar` under tag `blockera-core-v<version>`.
4. Update every compatible catalog entry's `version`, `fileName`, `url`,
   `sha256` and `size` together.
5. Run the Java build plus `cargo test -p theseus blockera_runtime` and the
   catalog tests. A bundled/catalog checksum mismatch is rejected by tests.

Supporting Fabric, Quilt, NeoForge or another Minecraft line requires a
separate core artifact per loader/game compatibility range; it must not reuse a
Forge JAR under a misleading manifest.
