# ADR 0002: Separate modular Fabric runtime for Minecraft 1.21.11

- Status: accepted for incremental implementation
- Date: 2026-07-21

## Context

The Forge 1.19.2 `blockera-core` line owns the current menu, chat and HUD.
Adding AI, targeting, voice and navigation directly to that UI would couple
rendering to input mutation and make future ports difficult. Minecraft
1.21.11 is the final obfuscated release before the 26.1 naming transition.

## Decision

Create `blockera-fabric-client` as an independent Fabric project using Mojang
mappings. Produce a public `blockera_core` JAR and a separate private
`blockera_ai_private` add-on. Keep four boundaries:

1. AI emits allow-listed high-level tool calls.
2. Deterministic controllers own camera, movement and inventory behavior.
3. Navigation is accessed only through `NavigationProvider`; Baritone is an
   optional reviewed adapter, not a foundation or bundled binary.
4. A synchronous emergency stop disables modules, cancels providers/tasks,
   clears confirmations, releases all key mappings and stops voice capture.

The public artifact contains only UI, fonts, HUD/widgets and first-party visual
modules. Whisper, targeting, navigation and model integration are excluded from
both its binary and sources JARs. Public manifests must never reference the
private add-on.

The first executable slice provides target selection and soft camera tracking
only in integrated singleplayer. It performs no attacks and rejects multiplayer
operation. Future multiplayer support must be explicit per trusted server and
must remain disabled by default.

## AI bridge contract

The mod connects only to `ws://127.0.0.1:<port>` or loopback `wss`. Provider
credentials live in a separately deployed local bridge and are never placed in
the mod JAR, its config or launcher manifest. Unknown tools are denied. Actions
such as dropping items require confirmation before a controller may execute
them.

## Consequences

- Existing Forge builds and manifests remain unchanged.
- UI can be ported incrementally against stable module contracts.
- Baritone can be replaced without changing UI or AI tool definitions.
- Fabric distribution requires a separate 1.21.11 profile and artifact; the
  Forge dev profile must never receive this JAR.
