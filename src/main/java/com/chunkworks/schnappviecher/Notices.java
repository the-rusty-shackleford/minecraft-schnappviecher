/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Localized notices; no mutable representation. */
public final class Notices {
    private Notices() {}
    /** requires: server thread and an escaped theft; effects: shows a short title to every online player in every dimension; throws: none. */
    public static void escaped(MinecraftServer server,Player victim,ItemStack item) {
        Component subtitle=Component.translatable("notice.schnappviecher.escaped",victim.getName());
        for(var player:server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10,70,20));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("notice.schnappviecher.title")));
            player.sendSystemMessage(Component.translatable("notice.schnappviecher.invoice",victim.getName(),item.getHoverName()));
        }
    }
}
