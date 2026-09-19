/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned timing knobs. AF: entries express ticks or minutes as named; RI: spec bounds. */
public final class PrankConfig {
    private PrankConfig() {}
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = B.comment("Schedule natural visits. Commands and eggs remain available.").define("enabled",true);
    public static final ModConfigSpec.IntValue MIN_MINUTES = B.defineInRange("minimumVisitMinutes",120,1,1440);
    public static final ModConfigSpec.IntValue MAX_MINUTES = B.defineInRange("maximumVisitMinutes",240,1,1440);
    public static final ModConfigSpec.IntValue COOLDOWN_MINUTES = B.defineInRange("playerCooldownMinutes",360,0,10080);
    public static final ModConfigSpec.IntValue STALK_TICKS = B.comment("Minimum stalking time before the first theft attempt; a random extra minute is added.").defineInRange("stalkTicks",1200,1,72000);
    public static final ModConfigSpec.IntValue CHASE_TICKS = B.comment("Minimum chase before forty unseen ticks and twelve blocks of distance count as escape.").defineInRange("chaseTicks",600,1,72000);
    public static final ModConfigSpec SPEC = B.build();
}
