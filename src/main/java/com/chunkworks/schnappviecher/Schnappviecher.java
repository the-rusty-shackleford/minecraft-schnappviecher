/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** Composition root; owns no mutable game state. */
@Mod(Schnappviecher.ID)
public final class Schnappviecher {
    public static final String ID = "schnappviecher";
    /** requires: loader-owned bus/container; effects: registers this standalone mod; throws: loader registration failures. */
    public Schnappviecher(IEventBus bus, ModContainer container) {
        Content.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, PrankConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(Visits::tick);
        NeoForge.EVENT_BUS.addListener(Visits::commands);
    }
}
