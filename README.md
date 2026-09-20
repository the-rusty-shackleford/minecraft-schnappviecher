# Schnappviecher

A standalone NeoForge 1.21.1 mod. A towering folklore prankster follows a player,
leaves an occasional quiet giggle as a clue, steals one item, and demands a snack to return it.
It is immortal; catching it and landing three spaced hits also recovers the item.

[**0.1.1**](https://github.com/the-rusty-shackleford/minecraft-schnappviecher/releases/tag/v0.1.1). Minecraft **1.21.1**, NeoForge **21.1.248 or newer
21.1.x**, Java **21**. Install the same jar on client and server. It has its own
entity, model, animation, sounds and behavior, with no dependency on Aberrant Mobs.

**0.1.1** fixes erratic escape routes and swimming, and makes the pre-theft
approach quieter and less conspicuous. It is part of the shared-pack 1.52.0 rollout;
use **Update Pack** in Prism when deployment is announced.

## The prank

- One Schnappviech visits at a time, normally every **2–4 hours**. It picks a
  survival/adventure player in the Overworld, with a **six-hour cooldown** per player.
- It appears behind its target and waits roughly 12 blocks behind them during
  its 1–2 minute stalking period, including while they look up or mine down.
  Stalking footsteps are soft; subdued giggles occur every 30–60 seconds.
  It freezes when that player looks at it, then closes in when its theft delay
  expires. It needs an open, unwatched approach and cannot steal through a wall.
- After stealing, it keeps a useful escape route instead of repeatedly switching
  direction. It can paddle across deep water, surface and climb out onto a bank.
  The louder chase/ransom sounds and recovery mechanics still apply.
- **Anything carried is fair game:** inventory, hotbar, equipped armor and offhand.
  It takes one item, preserving names, enchantments, damage and container contents.
  A stack of 64 loses one; a filled shulker box goes with its contents.
- Land **three hits**, at least half a second apart, to make it surrender. Friends
  can help. It never dies, attacks players or breaks blocks.
- After at least 30 seconds of escape, 12 blocks of distance and two seconds out
  of its target's gaze, everyone sees **“SCHNAPP! / <player> got got!”** The chat
  announces which item is being held pending a snack processing fee.
- It returns to loiter nearby. **Right-click with one cookie, bread or apple** to
  recover the item. Anyone can pay. Creative players need and spend nothing.
  Beating it still works after the announcement.
- Returned property drops toward its owner and is reserved for their pickup,
  protected from ordinary damage and automatic expiry. A full inventory leaves
  the drop on the ground. If the owner is offline when a friend pays, the return
  waits for their next login.
- Unpaid property survives ordinary saves, restarts, chunk unloading and dismissed
  visits. The creature revisits outstanding debts, including when natural visits
  are disabled. It never steals a second item while that player's debt exists.

## Commands and settings

| Command | Who | Effect |
| --- | --- | --- |
| `/schnappviecher visit <player>` | Operator | Start a visit at a safe, loaded site near a player. |
| `/schnappviecher dismiss` | Operator | End the current visit; retain any outstanding property. |
| `/schnappviecher status` | Any player | Show their outstanding item. |

The Schnappviech Spawn Egg is in the Creative Spawn Eggs tab. Extra eggs do not
bypass the one-visit limit. Survival/adventure players can be robbed; creative
and spectator players cannot.

The server's `config/schnappviecher-server.toml` contains the settings below. A copy
in the world's `serverconfig` directory can override them for that world, following
[NeoForge's server configuration rules](https://docs.neoforged.net/docs/1.21.1/misc/config/#configuration-types).

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Schedule new natural pranks. Existing debts remain recoverable. |
| `minimumVisitMinutes` | `120` | Minimum interval between natural visits. |
| `maximumVisitMinutes` | `240` | Maximum interval, treated as at least the minimum. |
| `playerCooldownMinutes` | `360` | Cooldown before targeting the same player again. |
| `stalkTicks` | `1200` | Minimum stalking time, plus 0–1200 random ticks. |
| `chaseTicks` | `600` | Minimum chase before escape can trigger. |

Twenty ticks are one second at normal server speed; intervals and cooldowns count
running game time, not time while the server is shut down. Natural visits require a safe
site in loaded chunks; the mod never force-loads chunks. Outstanding debts retry
placement about once per minute. The `schnappviecher:ransom_snacks` item tag can be
changed with a data pack; change the matching language text too if replacing snacks.

## Appearance and sound

The model has a shaggy horned head, long hinged wooden jaws, teeth, an uneven cloth
costume and visible feet. Its jaws giggle and hold the actual stolen item. English
and German names, notices and subtitles are included.

[Head close-up](devtools/art/preview/head.png) ·
[Public banner](devtools/art/preview/banner.png) ·
[Night view](devtools/art/preview/night.png) ·
[Giggle preview](devtools/art/preview/giggles.ogg)

Three short CC0 giggles are bundled, with jaw clacks and protest sounds using
Minecraft sound events. The requested reference is the creature around 0:09 in
[the Atlanta “Helen” trailer](https://www.youtube.com/watch?v=IPvlWSUqeFk).
The sound's resemblance to the reference **has not been verified by listening**;
this environment could inspect the video but could not audition audio. No FX audio
is distributed. See [asset provenance and reproduction](devtools/art/SOURCES.md).

## Recovery and saved data

The Overworld's `data/schnappviecher.dat` owns stolen ItemStacks. The entity holds
only a display copy and a reference to that ledger. Stale actors cannot settle or
steal from a replacement's claim. Keep the mod installed until debts are recovered.
Persistence follows normal Minecraft saves; it does not provide crash-atomic
transactions across player files, entity chunks and saved data.

## Development

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build
```

The jar is `build/libs/schnappviecher-0.1.1.jar`. `./gradlew test` exercises ten
JDK-only encounter/scheduling tests. `./gradlew build` also runs eighteen real-server
gametests: full ItemStack persistence, every carried slot, actual player attacks,
all three payments, offline-owner recovery, reserved pickup, stale actors, title
delivery to multiple players, natural spawning/navigation/theft/escape, stable
escape routes, river crossings, submerged and open-water escape, quieter stalking
sound packets, distance while looking down, and theft prevention while watched.
`-PskipGameTests` is a development shortcut, not the full validation gate.

After the first build, `./gradlew runPhotoBooth` runs a separate, self-closing client
that takes seven screenshots and verifies payment through a real client interaction
packet. It sets master volume to zero before launch. Use an available desktop or
the existing Xephyr display; only run one rendering client at a time. Put compatible
Iris/Sodium jars in `run/booth/mods` and select a shader in that profile when testing
shader compatibility. Captures appear under `run/booth/screenshots`.

Tests and booth code never ship in the production jar. The pure `domain` source set
cannot import Minecraft; `main` contains the game adapters; `gametest` contains the
real-server and client checks. See [project status](knowledge/PROJECT.md) and
[decisions](knowledge/decisions/README.md).

Copyright 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later. Third-party sound
recordings retain their documented licenses.
