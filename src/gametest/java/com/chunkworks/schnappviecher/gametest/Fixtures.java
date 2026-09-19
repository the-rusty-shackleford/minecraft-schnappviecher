/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

/** Actual vanilla players on the gametest server, with the framework's embedded transport.
 * Unlike makeMockServerPlayerInLevel these do not override isCreative to always true. */
final class Fixtures {
    private static final Map<UUID,EmbeddedChannel> CHANNELS=new HashMap<>();
    private Fixtures() {}
    static ServerPlayer player(GameTestHelper h,int x,int z) {
        return player(h,x,z,new GameProfile(UUID.randomUUID(),"Visitor"+x+z));
    }
    static ServerPlayer player(GameTestHelper h,int x,int z,GameProfile profile) {
        var cookie=CommonListenerCookie.createInitial(profile,false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        CHANNELS.put(player.getUUID(),channel);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.setNoGravity(true);
        var pos=h.absolutePos(new BlockPos(x,2,z));player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        player.setYHeadRot(0);player.setYBodyRot(0);
        return player;
    }
    static void floor(GameTestHelper h) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
    }
    static List<Object> packets(ServerPlayer p) {
        var channel=CHANNELS.get(p.getUUID());channel.runPendingTasks();var result=new ArrayList<Object>();
        Object packet;while((packet=channel.readOutbound())!=null)result.add(packet);return result;
    }
    static void remove(ServerPlayer p) {
        p.server.getPlayerList().remove(p);
        var channel=CHANNELS.remove(p.getUUID());if(channel!=null)channel.finishAndReleaseAll();
    }
}
