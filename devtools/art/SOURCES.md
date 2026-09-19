# Art and sound provenance

The model and deterministic UV atlas are authored in this repository by Rusty
Shackleford and nfx under AGPL-3.0-or-later. `build_texture.py` reproduces the atlas;
`client/SchnappModel.java` is the native Minecraft model and animation source.

Traditional silhouette reference, viewed 2026-09-18:
https://www.schnappviecher.com/images/110308c/data/images/umzug_schnappviecher_2011_027.jpg
The reference photograph is not distributed. It shows horned shaggy heads with
wooden snapping jaws above long cloth costumes.

The requested voice reference is the Schnappviech in FX's Atlanta, season 2 episode
4, "Helen". Rusty supplied https://www.youtube.com/watch?v=IPvlWSUqeFk ; the creature
appears around 0:09 in this 20-second public promo. The reference was downloaded to
the session scratchpad and its frames inspected on 2026-09-18. This environment
could not audition audio, so a listening comparison remains unverified. No episode
or trailer media is bundled in the repository or jar.

## Giggle candidates

MeanRaccoon, **male evil giggle male collection**, Freesound sound 829359:
https://freesound.org/people/MeanRaccoon/sounds/829359/

The source page explicitly identifies **Creative Commons 0**, verified 2026-09-18.
https://creativecommons.org/publicdomain/zero/1.0/
The bundled source preview (`sounds/giggles-source.ogg`) is 37.419 seconds,
downloaded from https://cdn.freesound.org/previews/829/829359_10956972-hq.ogg .
`sounds/build.sh` preserves the exact excerpts, mono conversion, EQ, modest pitch
shift, level adjustment and fades used to make three short variations.

Jaw clack and protest currently reference Minecraft's existing bamboo-wood button
and fox-hurt sound events. No Minecraft audio files are copied into the jar.

`preview/giggles.ogg` concatenates the three processed variants with 0.6 seconds
of silence between them. The PNG previews are actual booth captures from Minecraft
1.21.1, NeoForge 21.1.248, Iris/Sodium and Complementary Unbound r5.8.1. No compositing
or image generation is used for the previews. Minecraft and the shaders are not
distributed in this repository.
