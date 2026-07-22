# Blockera Fabric Runtime

This is the separate Minecraft 1.21.11 Fabric line. It does not replace or
modify the existing Forge 1.19.2 `blockera-core` project.

## Distribution split

The build produces two independent remapped artifacts:

- `blockera-core-fabric-0.1.0-dev.jar` — public UI/HUD runtime, custom fonts,
  hitboxes and fifty allow-listed local widgets. This is the only artifact that
  may be referenced by a public launcher manifest.
- `blockera-ai-private-0.1.0-dev.jar` — private add-on containing targeting,
  the loopback LLM bridge and local Whisper speech recognition. It depends on
  the public Core JAR and is not published for regular users.

The default Core JAR and its sources JAR explicitly exclude AI, voice,
targeting, automation and navigation packages.

## Current vertical slice

- Mojang mappings with Fabric Loom remap;
- first-party-only `ModuleManager` (`blockera:*` ids);
- deterministic target selection and acceleration-limited camera rotation;
- Blockera title screen, translucent Escape/Right Shift menu and Control Center;
- player target capture/soft lock for singleplayer, LAN and explicitly trusted servers;
- `NavigationProvider` with a typed adapter to the official Baritone 1.21.11 build;
- allow-listed AI tool registry and confirmation queue;
- loopback-only AI bridge configuration with no client secrets;
- emergency stop that releases input and cancels all active subsystems.
- a three-panel HUD editor with a widget library, isolated game viewport and
  inspector; only FPS is enabled by default;
- local push-to-talk transcription using an external audited `whisper.cpp`
  runtime and multilingual model from `config/blockera-ai/whisper`.

Default controls:

- `G`: capture the visible player nearest the crosshair; hold for soft lock;
- `H`: release target;
- `End`: emergency stop.
- hold `V`: record locally, release to transcribe and execute an allow-listed
  command; recording auto-stops after the configured maximum duration.
- `N`: open the private Baritone panel. It can navigate to the targeted block,
  follow the targeted entity, mine the targeted block type, explore within a
  bounded area, pause, resume and stop.
- `Right Shift`: open the Blockera menu without pausing the world.

Target assist is intentionally disabled on public servers. The decision boundary
is exposed through `TargetDecisionProvider`, so a future local LLM can choose a
target and desired look direction without receiving direct input or attack
control. No attack action is registered.

For a user-owned server with a public address, join it and use
`Right Shift -> Target -> Trust current server`. The normalized host is stored
locally in `config/blockera-client/trusted-servers.json`; trust is never applied
to every public server.

The private profile installs the official upstream 1.21.11 Fabric artifact as
a separate mod. Its exact commit, CI run and checksums are recorded in
`libs/BARITONE-PROVENANCE.md`. Neither Blockera JAR nests Baritone classes.
Voice and UI can only invoke typed, allow-listed navigation operations; chat
command control is disabled and `End` cancels navigation immediately.

Experimental local target ranking can use Ollama through
`config/blockera-client/ai-targeting.json`. The bridge sends anonymous
candidate geometry, validates the returned index and always keeps the
deterministic selector as a fallback.

## Build

```powershell
.\gradlew.bat clean build
```

On Windows, Gradle's test worker can misread a Unicode/OneDrive project path.
The repository verification uses a temporary ASCII drive mapping without
copying or moving project files:

```powershell
subst B: "$PWD"
Push-Location B:\
.\gradlew.bat clean build
Pop-Location
subst B: /D
```

Both remapped JARs are written to `build/libs`. Public distribution must ship
only `blockera-core-fabric`; the private AI JAR and Whisper model stay in the
owner's dev profile.

## Deliberately not bundled

- Baritone inside either Blockera JAR, or any unreviewed fork;
- remote API credentials;
- automatic attack, item drop, or command execution;
- unrestricted multiplayer automation;
- third-party modules/addons.
