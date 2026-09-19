# Schnappviecher

Standalone NeoForge 1.21.1 mod, `com.chunkworks.schnappviecher`, repository
`minecraft-schnappviecher`. A large traditional Wudele follows one player, giggles,
steals one item, and gives it back when beaten or bribed with a snack. It never dies.
Natural visits default to every 2–4 hours, with a six-hour cooldown per player
(D-0003). Targets are selected randomly; theft waits for an unwatched opportunity.

Rusty's current direction overrides the earlier suggestion to use Aberrant Mobs:
this is its own unique creature, with no dependency on or changes to that mod.
The sound reference is the Schnappviech in Atlanta, season 2 episode 4, "Helen".
Exact resemblance to that sound remains unverified by listening.

`domain` contains JDK-only encounter rules; `main` adapts them to the real game;
`gametest` contains real-server tests and the silent rendering booth. Art and sound
provenance belongs in `devtools/art/SOURCES.md`. Rusty explicitly authorized the 0.1.0 release on 2026-09-18.

Status, 2026-09-18: local 0.1.0 implemented. Ten domain JUnit tests and eleven
dedicated-server gametests passed in this session, including actual disk reopen,
offline payment followed by reconnect, owner-only pickup with a full inventory,
all ransom snacks, and natural placement/navigation/theft/escape. Real client
repayment also passed in a silent, self-closing booth using Iris/Sodium and
Complementary Unbound r5.8.1. Front, side, head, item, banner and night captures were
viewed; spawn-egg rendering was checked in the actual client. The title timing and
layout were corrected and rechecked. Previews live in `devtools/art/preview/`.

The natural escape fixture explicitly keeps its player looking away during the
chase: a fixed compass bearing did not establish the test's intended unwatched
condition as the random flee route curved. The production escape requirements
remain unchanged. The exact Atlanta audio resemblance remains unverified; the
bundled CC0 giggles need human listening review. Rusty approved release with that disclosed limitation.
