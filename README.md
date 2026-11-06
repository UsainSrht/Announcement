# Announcement

Scheduled chat announcements. **One jar** for Spigot, Paper, Purpur, Folia, Fabric, Forge and NeoForge,
Minecraft 1.16 up to 26.3, on servers and on clients.

Build: `mvn clean package` (always `clean`) -> `dist/target/Announcement-2.0.jar`. Needs a JDK 17+ to build; the jar
itself is Java 8 bytecode (the Paper-only part is Java 17 and only loads on Paper 1.18.2+).

## Install

| Platform | Where | Config |
|---|---|---|
| Spigot / Paper / Purpur / Folia | `plugins/` | `plugins/Announcement/config.yml` |
| Fabric / Forge / NeoForge server | `mods/` | `config/announcement/config.yml` |
| Fabric / Forge / NeoForge client | `mods/` | `config/announcement/config.yml` |

Fabric does not need Fabric API. A client without the mod can join a server that has it.

## Placeholders

`placeholders.per_player` (default `true`), or `per_player:` on a single announcement:

* **per player** - every player gets the announcement parsed with their own placeholders.
* **for everyone** - parsed once with global placeholders, the identical message goes to all players.

Sources: PlaceholderAPI (`%player_name%`, all platforms with PAPI), MiniPlaceholders (`<tag>`, Paper/Purpur/Folia,
both the 2.x and the 3.x API), and built in everywhere: `%player_name%`, `%player_displayname%`, `%server_time%`,
`%server_date%`.

## Rendering

* Paper / Purpur / Folia 1.18.2+: the server's own MiniMessage (everything it supports) plus the `<centered>` tag.
* Spigot, older Paper and every mod loader: a bundled MiniMessage subset - colors, `#hex`, bold/italic/underlined/
  strikethrough/obfuscated (and `<!tag>`), reset, newline, gradient, rainbow, centered, `click`,
  `hover:show_text`.

## How the mod side works (no per-version code)

Obfuscated names differ per loader and per Minecraft version, so the mod never uses one. It finds the running server
through loader API (`FabricLoader.getGameInstance()`, `ServerLifecycleHooks.getCurrentServer()`), then locates the
command manager, the command-source class and the console-source factory by the *shape* of their methods (JDK and
Brigadier types are stable). Announcements are `execute as @a run tellraw @s <json>` on the server thread, and
`/announcement` is registered on the live Brigadier dispatcher with the same permission rule as vanilla `/tellraw`.
On a client with no integrated server it prints into the local chat via `sendSystemMessage`.

`announcement.running` (system property) stops a hybrid server from running the plugin and the mod at once.

## Config parsing

`config.yml` is read by `MiniYaml` (core), a ~350-line reader for the YAML subset the config uses (block maps/lists,
quoted scalars, comments, `|`/`>` blocks, one-line `[a, b]` lists). No SnakeYAML in the jar. Anchors, tags and flow
maps are not supported.

## Telemetry

Spigot/Paper/Purpur/Folia report anonymous counts to [bStats](https://bstats.org) (opt out in `plugins/bStats/config.yml`):
renderer, placeholder mode (per player / everyone / mixed), placeholder providers, announcement-count and delay
buckets, ordered vs random, console broadcast, folia, and announcements sent per interval. Message text is never sent.
Mods send nothing (bStats has no mod-loader platform). **Set `BSTATS_PLUGIN_ID` in `bukkit/.../Telemetry.java`** after
registering the plugin on bstats.org; while it is 0 telemetry stays off.
