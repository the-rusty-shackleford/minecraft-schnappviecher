/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.client;
import com.chunkworks.schnappviecher.Content;
import com.chunkworks.schnappviecher.Schnappviecher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
/** Client-only registry hook. */
@EventBusSubscriber(modid=Schnappviecher.ID,value=Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}
    /** requires: renderer registration event; effects: registers our renderer; throws: none. */
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerEntityRenderer(Content.CREATURE.get(),SchnappRenderer::new);}
}
