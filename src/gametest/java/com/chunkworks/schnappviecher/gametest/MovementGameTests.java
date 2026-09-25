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
 * land-to-water-to-land and submerged-to-shore; a stalk rounding a wall to its post; a
 * spinning player moving the stalker nowhere (D-0005). Real AI, physics and navigation,
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

    /** Rusty (2026-09-25): "it gets stuck at walls because it does not find the way to an
     * opening". A long wall stands between the creature and its stand-off point behind the
     * player, with the only gap at the far end; the stalk must round the wall and reach the
     * player's side, not stand at the wall replanning the same partial path forever. */
    @GameTest(template="field",batch="movement-wall",timeoutTicks=450)
    public void stalkRoundsAWallToReachItsPlace(GameTestHelper h) {
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        // The wall runs east-west at z=30 from x=33 to x=62, three high, thirty blocks the
        // creature cannot see over; the only way round is past x=63. The detour to the
        // stand-off point is some thirty-three blocks, well inside the follow range.
        for(int x=33;x<=62;x++)for(int y=2;y<=4;y++)h.setBlock(new BlockPos(x,y,30),Blocks.STONE);
        var owner=Fixtures.player(h,48,48);
        // Facing +z (yaw 0): the stand-off point twelve behind is (48, 36), south of the wall.
        var s=stalker(h,owner,48,2,24);
        var wall=h.absolutePos(new BlockPos(48,2,30));
        double[] nearestToWall={Double.MAX_VALUE};int[] replans={0};BlockPos[] previous={null};
        h.onEachTick(()->{
            nearestToWall[0]=Math.min(nearestToWall[0],Math.abs(s.getZ()-wall.getZ()));
            var path=s.getNavigation().getPath();
            if(path!=null){var end=path.getTarget();if(previous[0]!=null&&!previous[0].equals(end))replans[0]++;previous[0]=end;}
        });
        h.runAtTickTime(400,()->{
            double southOfWall=s.getZ()-wall.getZ();double gap=s.distanceTo(owner);
            boolean stalking=s.stage()==Encounter.Stage.STALK;
            String details="z past the wall="+southOfWall+", distance to player="+gap+", nearest to wall="+nearestToWall[0]+", replans="+replans[0]+", path="+s.getNavigation().getPath();
            cleanup(s,owner);
            h.assertTrue(stalking,"still stalking: "+details);
            h.assertTrue(southOfWall>1,"the creature rounds the wall to the player's side: "+details);
            h.assertTrue(gap>=6&&gap<=18,"and waits at its stand-off distance: "+details);
            h.succeed();
        });
    }

    /** Rusty (2026-09-25): "a target moving around flips it back and forth with no net
     * movement". The player spins in place; the stand-off point behind them jumps to the far
     * side every turn and the creature reverses after each. It must settle near the ring and
     * not reverse its heading more than a couple of times. */
    @GameTest(template="field",batch="movement-spin",timeoutTicks=500)
    public void aSpinningPlayerDoesNotFlipTheCreatureBackAndForth(GameTestHelper h) {
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var owner=Fixtures.player(h,48,48);
        var s=stalker(h,owner,48,2,36);
        Vec3[] last={s.position()};Vec3[] heading={Vec3.ZERO};int[] reversals={0};double[] walked={0};
        h.onEachTick(()->{
            // The player turns a quarter every twenty ticks, four full turns in the run.
            if(s.tickCount%20==0){float yaw=(owner.getYRot()+90)%360;owner.setYRot(yaw);owner.setYHeadRot(yaw);owner.setYBodyRot(yaw);}
            var now=s.position();var step=new Vec3(now.x-last[0].x,0,now.z-last[0].z);last[0]=now;
            if(step.lengthSqr()<.0025)return;
            walked[0]+=step.length();
            if(heading[0].lengthSqr()>0&&heading[0].dot(step)<0)reversals[0]++;
            heading[0]=step;
        });
        h.runAtTickTime(400,()->{
            double gap=s.distanceTo(owner);
            String details="reversals="+reversals[0]+", walked="+walked[0]+", distance to player="+gap;
            cleanup(s,owner);
            h.assertTrue(reversals[0]<=2,"the creature does not flip back and forth: "+details);
            h.assertTrue(gap>=6&&gap<=18,"and keeps its stand-off distance: "+details);
            h.assertTrue(walked[0]<80,"without running laps around the player: "+details);
            h.succeed();
        });
    }

    /** effects: a stalking creature bound to the owner, before its first theft attempt. */
    private static Schnappviech stalker(GameTestHelper h,ServerPlayer owner,int x,int y,int z) {
        var active=Visits.active(owner.server);if(active!=null)active.park();
        var s=Content.CREATURE.get().create(h.getLevel());
        var pos=h.absolutePos(new BlockPos(x,y,z));s.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
        h.getLevel().addFreshEntity(s);s.begin(owner);
        s.getRandom().setSeed(4242L);
        return s;
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
