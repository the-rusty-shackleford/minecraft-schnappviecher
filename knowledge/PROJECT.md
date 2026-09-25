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

## 0.1.2, built 2026-09-25, unreleased

Rusty, on 0.1.1: stuck at walls (no way to an opening found), and a target moving around
flipped it back and forth with no net movement. Both were reproduced on the unchanged
creature by two new GameTests before any fix (a thirty-block wall between the creature and
its post: 1.3 blocks from the wall for the whole run on a one-node route; a player turning a
quarter every second: five reversals). [D-0005](decisions/D-0005.md): the pure
`domain.Standoff` gives the stalk a post on the ring round the victim, kept while the victim
stays within nine to fifteen blocks of it (a turn moves it nowhere), and when a new one is
needed the candidates out of the victim's view nearest the creature; the creature walks the
first candidate a route reaches, or the route ending nearest one and holds there five seconds;
the navigation's search budget is four times vanilla's. Sixteen JUnit, twenty GameTests, the
booth under Complementary Unbound with a wall scene of three shots; see
[verification](../devtools/verification/0.1.2.md). Committed, not tagged; waits on Rusty's go
for pack 1.64.0.

## Current release: 0.1.1

On 2026-09-20 Rusty requested reliable fleeing/swimming and subtler stalking
([D-0004](decisions/D-0004.md)). The checkout now implements those changes.
A clean build passed ten JUnit tests and eighteen real-server tests, including
three new stealth regressions and four movement regressions. The old behavior
failed the added spacing, early-giggle and navigation cases before their fixes.
See [verification](../devtools/verification/0.1.1.md).

Published and deployed on 2026-09-20 with C.A.M.P. 0.2.0 in pack 1.52.0.
The server restarted after the authorized 120-second warning and countdown
reminders, loaded the matching artifact and reported 20 TPS. Mod Hub reports the
server matches the published pack.
