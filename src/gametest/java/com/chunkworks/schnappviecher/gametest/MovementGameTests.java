/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Partitions: fixed victim with competing bystanders; unfinished escape routes;
 * land-to-water-to-land and submerged-to-shore. Real AI, physics and navigation,
 * deterministic random seeds, no substituted movement controllers. */
@GameTestHolder("schnappviecher")
@PrefixGameTestTemplate(false)
public final class MovementGameTests {
    @GameTest(template="field",batch="movement-escape",timeoutTicks=180)
    public void escapeCommitsToRoutesAndKeepsTheSameVictim(GameTestHelper h) {
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var owner=Fixtures.player(h,48,48);
        var bystander=Fixtures.player(h,51,48);
        var s=actor(h,owner,Encounter.Stage.CHASE,48,2,44);
        s.getRandom().setSeed(12345L);
        BlockPos[] previous={null};int[] discardedRoutes={0};double[] furthest={0};
        h.onEachTick(()->{
            var path=s.getNavigation().getPath();
            if(path!=null&&!path.isDone()) {
                BlockPos end=path.getTarget();
                if(previous[0]!=null&&!previous[0].equals(end)
                        &&s.position().distanceToSqr(Vec3.atBottomCenterOf(previous[0]))>9)discardedRoutes[0]++;
                previous[0]=end;
            }
            furthest[0]=Math.max(furthest[0],s.distanceTo(owner));
        });
        h.runAtTickTime(120,()->{
            CompoundTag state=new CompoundTag();s.save(state);
            int changes=discardedRoutes[0];double progress=furthest[0];
            boolean same=state.getUUID("Victim").equals(owner.getUUID());
            cleanup(s,owner);Fixtures.remove(bystander);
            h.assertTrue(same,"bystander cannot steal the encounter target");
            h.assertTrue(changes<=3,"escape repeatedly abandons unfinished routes: "+changes);
            h.assertTrue(progress>=14,"escape makes useful progress: "+progress);
            h.succeed();
        });
    }

    @GameTest(template="arena",batch="movement-water-cross",timeoutTicks=450)
    public void crossesDeepWaterAndClimbsTheFarBank(GameTestHelper h) {
        water(h,false);
    }

    @GameTest(template="arena",batch="movement-water-submerged",timeoutTicks=450)
    public void submergedCreatureSurfacesAndReachesDryLand(GameTestHelper h) {
        water(h,true);
    }

    @GameTest(template="arena",batch="movement-water-escape",timeoutTicks=350)
    public void escapesThroughWaterInsteadOfRejectingEveryWetDestination(GameTestHelper h) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),Blocks.WATER);
        }
        var owner=Fixtures.player(h,26,16);
        var origin=h.absolutePos(new BlockPos(26,5,16));
        owner.moveTo(origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
        var s=actor(h,owner,Encounter.Stage.CHASE,22,5,16);
        s.getRandom().setSeed(8128L);
        double start=s.distanceTo(owner);double[] furthest={start};boolean[] wetRoute={false};
        h.onEachTick(()->{
            furthest[0]=Math.max(furthest[0],s.distanceTo(owner));
            wetRoute[0]|=s.isInWater()&&!s.getNavigation().isDone();
        });
        h.runAtTickTime(200,()->{
            double progress=furthest[0]-start;cleanup(s,owner);
            h.assertTrue(wetRoute[0],"a real escape route is accepted in open water");
            h.assertTrue(progress>=8,"water escape increases separation by eight blocks: "+progress);
            h.succeed();
        });
    }

    private static void water(GameTestHelper h,boolean submerged) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
            for(int y=2;y<=5;y++)h.setBlock(new BlockPos(x,y,z),x>=8&&x<=23?Blocks.WATER:Blocks.STONE);
        }
        var owner=Fixtures.player(h,31,16);
        var destination=h.absolutePos(new BlockPos(31,6,16));
        owner.moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5,0,0);
        var s=actor(h,owner,Encounter.Stage.RANSOM,submerged?14:4,submerged?2:6,16);
        s.getRandom().setSeed(654321L);
        boolean[] entered={false};double[] maxX={s.getX()};
        h.onEachTick(()->{entered[0]|=s.isInWater();maxX[0]=Math.max(maxX[0],s.getX());});
        h.runAtTickTime(400,()->{
            boolean dry=!s.isInWater()&&s.onGround();
            boolean reached=s.getX()>h.absolutePos(new BlockPos(24,6,16)).getX();
            String details="position="+s.position()+", dry="+dry+", maxX="+maxX[0]+", path="+s.getNavigation().getPath();
            cleanup(s,owner);
            h.assertTrue(entered[0],"fixture exercises actual water");
            h.assertTrue(reached&&dry,"must swim and climb the far bank: "+details);
            h.succeed();
        });
    }

    private static Schnappviech actor(GameTestHelper h,ServerPlayer owner,Encounter.Stage stage,int x,int y,int z) {
        var active=Visits.active(owner.server);if(active!=null)active.park();
        var s=Content.CREATURE.get().create(h.getLevel());
        var pos=h.absolutePos(new BlockPos(x,y,z));s.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        h.getLevel().addFreshEntity(s);s.begin(owner);
        owner.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        h.assertTrue(Claims.get(owner.server).steal(s.getUUID(),owner,0),"real ledger owns one stolen item");
        if(stage==Encounter.Stage.RANSOM)Claims.get(owner.server).announce(owner.getUUID());
        CompoundTag tag=new CompoundTag();s.save(tag);tag.putString("PrankStage",stage.name());s.load(tag);
        return s;
    }

    private static void cleanup(Schnappviech s,ServerPlayer owner) {
        s.park();Claims.get(owner.server).settle(owner.getUUID());Claims.get(owner.server).deliver(owner,null);Fixtures.remove(owner);
    }
}
