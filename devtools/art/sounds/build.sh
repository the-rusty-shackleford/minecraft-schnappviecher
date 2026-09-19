#!/bin/sh
# Copyright (C) 2026 Rusty Shackleford and nfx. AGPL-3.0-or-later.
set -eu
cd "$(dirname "$0")"
out=../../../src/main/resources/assets/schnappviecher/sounds
mkdir -p "$out"
ffmpeg -v error -y -ss 0.75 -t 1.2 -i giggles-source.ogg -ac 1 -af 'highpass=f=90,lowpass=f=5500,asetrate=48000,aresample=44100,loudnorm=I=-20:TP=-3:LRA=7,afade=t=in:d=0.025,afade=t=out:st=1.02:d=0.08' -ar 44100 -c:a libvorbis -q:a 6 "$out/giggle1.ogg"
ffmpeg -v error -y -ss 6.2 -t 1.45 -i giggles-source.ogg -ac 1 -af 'highpass=f=90,lowpass=f=5500,asetrate=48000,aresample=44100,loudnorm=I=-20:TP=-3:LRA=7,afade=t=in:d=0.025,afade=t=out:st=1.22:d=0.1' -ar 44100 -c:a libvorbis -q:a 6 "$out/giggle2.ogg"
ffmpeg -v error -y -ss 8.58 -t 1.86 -i giggles-source.ogg -ac 1 -af 'highpass=f=90,lowpass=f=5500,asetrate=46000,aresample=44100,loudnorm=I=-20:TP=-3:LRA=7,afade=t=in:d=0.025,afade=t=out:st=1.65:d=0.12' -ar 44100 -c:a libvorbis -q:a 6 "$out/giggle3.ogg"
