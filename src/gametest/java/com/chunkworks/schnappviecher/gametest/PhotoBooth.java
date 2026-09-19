/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * One silent, self-closing real client. Steps wait for rendered frames and server
 * acknowledgements, so tick catch-up cannot silently skip a photograph. Verifies
 * a real client interaction packet pays the fee. Screenshots are judged separately.
 */
@EventBusSubscriber(modid=TestMod.ID,value=Dist.CLIENT)
public final class PhotoBooth {
    private PhotoBooth() {}
    private static final boolean ACTIVE=Boolean.getBoolean("schnappviecher.booth");
    private static final AtomicInteger SAVED=new AtomicInteger();
    private static volatile boolean ready;
    private static volatile String failure;
    private static volatile UUID creatureId;
    private static int stage,frames;
    private static long started;
    private static Schnappviech creature;

    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if(!ACTIVE)return;
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null)return;
        if(started==0)started=System.currentTimeMillis();
        if(failure!=null||System.currentTimeMillis()-started>480000) {
            LogUtils.getLogger().error("SCHNAPP BOOTH FAILED: {}",failure==null?"render timeout":failure);mc.stop();return;
        }
        if(stage==0){mc.options.hideGui=true;stage=1;server(mc,PhotoBooth::setup);return;}
        if(!ready||mc.screen!=null||mc.getOverlay()!=null)return;
        // Capture the timed title while visible even when software shaders render slowly.
        if(++frames<(stage==5?8:35))return;
        frames=0;
        switch(stage++) {
            case 1 -> {shot(mc,"01-front");server(mc,p->camera(p,6,101,-6,45,2));}
            case 2 -> {shot(mc,"02-side");server(mc,p->camera(p,-2,102,-3,-34,12));}
            case 3 -> {
                shot(mc,"03-head");server(mc,p->{
                    p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
                    camera(p,0,100,2,0,0);p.setYRot(0);p.setYHeadRot(0);
                    require(creature.trySteal(p),"real inventory theft");
                    camera(p,0,100,-7,0,-8);
                });
            }
            case 4 -> {
                if(mc.getItemRenderer().getModel(new ItemStack(Content.EGG.get()),mc.level,mc.player,0)==mc.getModelManager().getMissingModel()) {
                    failure="spawn egg model is missing";return;
                }
                shot(mc,"04-stolen-item");mc.options.hideGui=false;server(mc,p->{
                    p.getInventory().setItem(8,new ItemStack(Content.EGG.get()));p.containerMenu.broadcastChanges();
                    CompoundTag tag=new CompoundTag();creature.save(tag);tag.putString("PrankStage","RANSOM");creature.load(tag);
                    Claims.get(p.server).announce(p.getUUID());Notices.escaped(p.server,p,creature.heldItem());
                });
            }
            case 5 -> {
                shot(mc,"05-banner");server(mc,p->{camera(p,0,100,-2.2,0,-20);p.getInventory().setItem(0,new ItemStack(Items.COOKIE,3));p.containerMenu.broadcastChanges();});
            }
            case 6 -> {
                Schnappviech visible=null;
                for(var s:mc.level.getEntitiesOfClass(Schnappviech.class,mc.player.getBoundingBox().inflate(16)))
                    if(s.getUUID().equals(creatureId))visible=s;
                if(visible==null){failure="client never tracked creature";return;}
                mc.gameMode.interact(mc.player,visible,InteractionHand.MAIN_HAND);
            }
            case 7 -> {
                server(mc,p->{require(!Claims.get(p.server).owes(p.getUUID()),"client right-click settled claim");
                    require(p.getMainHandItem().getCount()==2,"client right-click spent one cookie");camera(p,0,100,-7,0,-8);});
            }
            case 8 -> {
                shot(mc,"06-paid");mc.options.hideGui=true;server(mc,p->{
                    p.serverLevel().setDayTime(18000);
                    p.serverLevel().setBlockAndUpdate(new BlockPos(-2,100,-3),Blocks.LANTERN.defaultBlockState());
                    creature.moveTo(2,100,2,180,0);creature.setYHeadRot(180);creature.setYBodyRot(180);
                    camera(p,0,100,-8,0,-7);
                });
            }
            case 9 -> shot(mc,"07-night");
            default -> {if(SAVED.get()>=7){LogUtils.getLogger().info("SCHNAPP BOOTH PASSED: theft, real client repayment, seven saved renders");mc.stop();}}
        }
    }
    private static void setup(ServerPlayer p) {
        PrankConfig.ENABLED.set(false);var level=p.serverLevel();
        var active=Visits.active(p.server);if(active!=null)active.park();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,p.server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
        level.setDayTime(6000);
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++) {
            level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.GRASS_BLOCK.defaultBlockState());
            for(int y=100;y<=110;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        for(int x:new int[]{4,-7,10}) {
            for(int y=100;y<106;y++)level.setBlockAndUpdate(new BlockPos(x,y,4),Blocks.OAK_LOG.defaultBlockState());
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int y=104;y<=107;y++)
                if(dx!=0||dz!=0)level.setBlockAndUpdate(new BlockPos(x+dx,y,4+dz),Blocks.OAK_LEAVES.defaultBlockState());
        }
        p.setGameMode(GameType.SURVIVAL);p.setHealth(20);p.getFoodData().setFoodLevel(20);p.getInventory().clearContent();
        camera(p,0,100,-7,0,-8);
        creature=Content.CREATURE.get().create(level);creature.moveTo(0,100,0,180,0);creature.setYHeadRot(180);creature.setYBodyRot(180);
        level.addFreshEntity(creature);creature.begin(p);creature.setNoAi(true);creatureId=creature.getUUID();
    }
    private static void camera(ServerPlayer p,double x,double y,double z,float yaw,float pitch) {
        p.connection.teleport(x,y,z,yaw,pitch);p.setYHeadRot(yaw);p.setYBodyRot(yaw);p.setNoGravity(true);
    }
    private static void require(boolean ok,String label){if(!ok)throw new IllegalStateException(label);}
    private static void server(Minecraft mc,Consumer<ServerPlayer> action) {
        ready=false;UUID id=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            try{action.accept(mc.getSingleplayerServer().getPlayerList().getPlayer(id));}
            catch(Exception e){failure=e.toString();LogUtils.getLogger().error("booth action failed",e);}
            finally{ready=true;}
        });
    }
    private static void shot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,"schnapp-"+name+".png",mc.getMainRenderTarget(),message->{
            LogUtils.getLogger().info("SCHNAPP CAPTURE {}: {}",name,message.getString());SAVED.incrementAndGet();
        });
    }
}
