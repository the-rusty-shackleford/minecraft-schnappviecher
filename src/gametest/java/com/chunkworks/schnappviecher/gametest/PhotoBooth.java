/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.mojang.authlib.GameProfile;
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
 * a real client interaction packet pays the fee, and that a stalk rounds a wall to its
 * post behind a victim (D-0005). Screenshots are judged separately.
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
    private static long started,sceneDue;
    private static Schnappviech creature,stalker;
    private static ServerPlayer victim;

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
        // The wall scene is paced by the server's clock: the creature walks between shots.
        if(stage>=11&&mc.level.getGameTime()<sceneDue)return;
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
            case 9 -> {shot(mc,"07-night");server(mc,PhotoBooth::wallScene);}
            // The stalk round a wall (D-0005): the creature, held still for the first shot,
            // stands beyond a wall from its post behind a victim who looks the other way; the
            // real player is the camera. Then it walks: round the wall's end, and to its post.
            case 10 -> {shot(mc,"08-wall-start");server(mc,p->stalker.setNoAi(false));sceneDue=mc.level.getGameTime()+100;}
            case 11 -> {shot(mc,"09-wall-round");sceneDue=mc.level.getGameTime()+220;}
            case 12 -> {shot(mc,"10-wall-post");server(mc,PhotoBooth::wallDone);}
            default -> {if(SAVED.get()>=10){LogUtils.getLogger().info("SCHNAPP BOOTH PASSED: theft, real client repayment, the stalk round a wall, ten saved renders");mc.stop();}}
        }
    }
    /** effects: daylight again; a stone-brick wall across the platform's north part with its only
     * gap at the east end; a weightless victim south of it at the platform's middle looking
     * south, so the whole ring of posts round them lies south of the wall and the nearest to the
     * creature is the one straight behind them, just south of the wall; a new stalking creature
     * north of the wall, its AI off until the first shot; the player as a camera high to the east. */
    private static void wallScene(ServerPlayer p) {
        var level=p.serverLevel();
        level.setDayTime(6000);
        level.setBlockAndUpdate(new BlockPos(-2,100,-3),Blocks.AIR.defaultBlockState());
        for(int x=-20;x<=18;x++)for(int y=100;y<=102;y++)level.setBlockAndUpdate(new BlockPos(x,y,-9),Blocks.STONE_BRICKS.defaultBlockState());
        victim=Fixtures.player(level,.5,100,4.5,0,new GameProfile(UUID.randomUUID(),"Victim"));
        var active=Visits.active(p.server);if(active!=null)active.park();
        stalker=Content.CREATURE.get().create(level);stalker.moveTo(4.5,100,-14.5,180,0);stalker.setYHeadRot(180);stalker.setYBodyRot(180);
        level.addFreshEntity(stalker);stalker.begin(victim);stalker.setNoAi(true);
        camera(p,27,110,-3,90,32);
    }
    /** effects: requires the creature south of the wall at a post on the ring round the victim
     * and out of their view (which post is the search's choice, D-0005), then parks it and
     * removes the victim. */
    private static void wallDone(ServerPlayer p) {
        double gap=Math.hypot(stalker.getX()-victim.getX(),stalker.getZ()-victim.getZ());
        boolean south=stalker.getZ()>-8,onRing=gap>=9&&gap<=15,behind=stalker.getZ()-victim.getZ()<gap*.5;
        LogUtils.getLogger().info("SCHNAPP BOOTH wall: the creature stands at {}, {} from the victim, south of the wall {}",stalker.position(),gap,south);
        try{require(south&&onRing&&behind,"the stalk rounds the wall to a post on the ring out of the victim's view: "+stalker.position()+", "+gap+" from the victim");}
        finally{stalker.park();Fixtures.remove(victim);}
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
