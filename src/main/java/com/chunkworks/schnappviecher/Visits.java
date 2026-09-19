/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;

import com.chunkworks.schnappviecher.domain.VisitSchedule;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/** Bounded server scheduler: one check per second, no entity/world scans or forced chunks. */
public final class Visits {
    private Visits() {}
    /** requires: server event; effects: delivers returns and schedules one active prank; throws: none. */
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server=event.getServer();long now=server.overworld().getGameTime();
        if(now%20!=0)return;
        Claims claims=Claims.get(server);
        for(ServerPlayer player:server.getPlayerList().getPlayers())claims.deliver(player,null);
        if(active(server)!=null)return;
        if(claims.actor()!=null){claims.actor(null);claims.nextVisit(Math.min(claims.nextVisit(),now+1200));}
        if(claims.nextVisit()==0){schedule(server);return;}
        if(now<claims.nextVisit())return;
        // Outstanding property takes priority, even when natural visits are disabled.
        for(ServerPlayer player:server.getPlayerList().getPlayers()) {
            if(claims.owes(player.getUUID())&&player.isAlive()&&!player.isSpectator()&&visit(player)!=null)return;
        }
        if(PrankConfig.ENABLED.get()) {
            ServerPlayer chosen=null;int eligible=0;
            for(ServerPlayer player:server.getPlayerList().getPlayers()) {
                if(player.level().dimension()==Level.OVERWORLD&&claims.eligible(player,now)
                        &&server.overworld().random.nextInt(++eligible)==0)chosen=player;
            }
            if(chosen!=null&&visit(chosen)!=null)return;
        }
        claims.nextVisit(now+1200);
    }
    /** requires: server thread; effects: returns the currently loaded authorized actor, without loading chunks; throws: none. */
    @Nullable public static Schnappviech active(MinecraftServer server) {
        var id=Claims.get(server).actor();if(id==null)return null;
        for(ServerLevel level:server.getAllLevels()) {
            Entity e=level.getEntity(id);if(e instanceof Schnappviech s&&!s.isRemoved())return s;
        }return null;
    }
    private static void schedule(MinecraftServer server) {
        int min=PrankConfig.MIN_MINUTES.get()*1200;
        int max=Math.max(min,PrankConfig.MAX_MINUTES.get()*1200);
        Claims.get(server).nextVisit(server.overworld().getGameTime()+VisitSchedule.interval(min,max,server.overworld().random.nextDouble()));
    }
    /** requires: server thread; effects: places a visit at a loaded, safe site behind the player, or returns null; throws: none. */
    @Nullable public static Schnappviech visit(ServerPlayer player) {
        var server=player.getServer();if(server==null||active(server)!=null)return null;
        ServerLevel level=player.serverLevel();
        Schnappviech creature=Content.CREATURE.get().create(level);if(creature==null)return null;
        Vec3 look=player.getLookAngle();double heading=Math.atan2(-look.z,-look.x);
        for(int i=0;i<24;i++) {
            double angle=heading+(level.random.nextDouble()-.5)*2.3;
            double distance=16+level.random.nextInt(13);
            int x=(int)Math.floor(player.getX()+Math.cos(angle)*distance),z=(int)Math.floor(player.getZ()+Math.sin(angle)*distance);
            BlockPos column=new BlockPos(x,player.getBlockY(),z);
            if(!level.hasChunkAt(column))continue;
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            for(int offset=-4;offset<=5;offset++) {
                int y=offset==5?surface:player.getBlockY()+offset;
                if(Math.abs(y-player.getY())>16)continue;
                BlockPos feet=new BlockPos(x,y,z);
                if(!level.getBlockState(feet.below()).isSolidRender(level,feet.below())||!level.getFluidState(feet).isEmpty())continue;
                creature.moveTo(x+.5,y,z+.5,player.getYRot()+180,0);
                if(!level.noCollision(creature,creature.getBoundingBox())||!level.isUnobstructed(creature))continue;
                if(level.addFreshEntity(creature)) {
                    creature.begin(player);schedule(server);return creature;
                }
            }
        }return null;
    }
    /** requires: command registration event; effects: exposes operator visit/dismiss and a player's property status; throws: none. */
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("schnappviecher")
            .then(Commands.literal("visit").requires(s->s.hasPermission(2)).then(Commands.argument("player",EntityArgument.player()).executes(c->{
                ServerPlayer player=EntityArgument.getPlayer(c,"player");
                Schnappviech s=visit(player);
                c.getSource().sendSuccess(()->Component.translatable(s==null?"command.schnappviecher.no_site":"command.schnappviecher.visit"),false);
                return s==null?0:1;
            })))
            .then(Commands.literal("dismiss").requires(s->s.hasPermission(2)).executes(c->{
                Schnappviech s=active(c.getSource().getServer());if(s!=null)s.park();
                c.getSource().sendSuccess(()->Component.translatable("command.schnappviecher.dismiss"),false);return 1;
            }))
            .then(Commands.literal("status").executes(c->{
                ServerPlayer player=c.getSource().getPlayerOrException();var item=Claims.get(c.getSource().getServer()).display(player.getUUID());
                c.getSource().sendSuccess(()->Component.translatable(item.isEmpty()?"command.schnappviecher.clear":"command.schnappviecher.owed",item.getHoverName()),false);return 1;
            })));
    }
}
