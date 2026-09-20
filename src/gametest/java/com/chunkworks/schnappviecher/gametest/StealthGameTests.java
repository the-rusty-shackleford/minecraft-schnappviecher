/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

/** Partitions: looking down while stalking; delayed quiet sound cues; direct observation
 * still halts approach. Exercises real navigation, ticking and outbound sound packets. */
@GameTestHolder("schnappviecher")
@PrefixGameTestTemplate(false)
public final class StealthGameTests {
    @GameTest(template="arena",batch="stealth-distance",timeoutTicks=250)
    public void lookingDownDoesNotBringTheWaitingCreatureIntoYourBoots(GameTestHelper h) {
        Fixtures.floor(h);
        var player=Fixtures.player(h,16,16);
        player.setXRot(89);
        var s=spawn(h,player);
        h.runAtTickTime(180,()->{
            double gap=s.distanceTo(player);
            s.park();Fixtures.remove(player);
            h.assertTrue(gap>=9,"waiting creature keeps its distance even when mining down: "+gap);
            h.succeed();
        });
    }

    @GameTest(template="arena",batch="stealth-sound",timeoutTicks=1250)
    public void stalkHasRareQuietGigglesRatherThanAnnouncingItselfImmediately(GameTestHelper h) {
        Fixtures.floor(h);
        var player=Fixtures.player(h,16,16);
        var s=spawn(h,player);
        int[] elapsed={0}, first={-1}, giggles={0};
        float[] loudest={0};
        boolean[] collecting={true};
        Fixtures.packets(player);
        h.onEachTick(()->{
            if(!collecting[0])return;
            elapsed[0]++;
            for(var packet:Fixtures.packets(player))
                if(packet instanceof ClientboundSoundPacket sound&&sound.getSound().value()==Content.GIGGLE.get()) {
                    if(first[0]<0)first[0]=elapsed[0];
                    giggles[0]++;
                    loudest[0]=Math.max(loudest[0],sound.getVolume());
                }
        });
        h.runAtTickTime(1201,()->{
            collecting[0]=false;
            s.park();Fixtures.remove(player);
            h.assertTrue(first[0]>=600,"first stalking giggle waits at least thirty seconds: "+first[0]);
            h.assertTrue(giggles[0]>=1&&giggles[0]<=2,"rare audible clues remain: "+giggles[0]);
            h.assertTrue(loudest[0]<=.2f,"stalking giggle is subdued: "+loudest[0]);
            h.succeed();
        });
    }

    @GameTest(template="arena",batch="stealth-watched",timeoutTicks=160)
    public void watchingStillStopsAnEligibleTheft(GameTestHelper h) {
        Fixtures.floor(h);
        var player=Fixtures.player(h,16,16);
        player.setYRot(180);player.setYHeadRot(180);
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        var s=spawn(h,player);
        var position=h.absolutePos(new BlockPos(16,2,14));
        s.moveTo(position.getX()+.5,position.getY(),position.getZ()+.5,0,0);
        // Look directly at the eyes, including this tall creature's vertical offset.
        player.setXRot((float)-Math.toDegrees(Math.atan2(s.getEyeY()-player.getEyeY(),2)));
        CompoundTag tag=new CompoundTag();s.save(tag);tag.putInt("StealAfter",1);s.load(tag);
        var start=s.position();
        h.runAtTickTime(100,()->{
            boolean seen=s.watched(), stalking=s.stage()==Encounter.Stage.STALK;
            double moved=s.position().distanceTo(start);
            boolean retained=player.getInventory().getItem(0).is(Items.DIAMOND);
            s.park();Fixtures.remove(player);
            h.assertTrue(seen&&stalking&&retained,"looking at the creature still prevents theft");
            h.assertTrue(moved<.5,"watched stalk stays still: "+moved);
            h.succeed();
        });
    }

    private static Schnappviech spawn(GameTestHelper h,net.minecraft.server.level.ServerPlayer player) {
        var old=Visits.active(player.server);if(old!=null)old.park();
        var s=Content.CREATURE.get().create(h.getLevel());
        var p=h.absolutePos(new BlockPos(16,2,4));
        s.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        h.getLevel().addFreshEntity(s);s.begin(player);
        return s;
    }
}
