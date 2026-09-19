/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Partitions: actual disk close/reopen; all online recipients; natural AI approach,
 * watched pause, theft and escape. Uses the production scheduler, navigation,
 * interaction state and transport, with test-only waiting shortened via saved state. */
@GameTestHolder("schnappviecher")
@PrefixGameTestTemplate(false)
public final class WorldGameTests {
    @GameTest(template="arena",batch="disk")
    public void actualSavedDataStorageReopensUnpaidAndPrepaidProperty(GameTestHelper h) throws Exception {
        Fixtures.floor(h);var p=Fixtures.player(h,4,4);
        Path base=Path.of("schnapp-test-data");Files.createDirectories(base);
        Path dir=Files.createTempDirectory(base,"claims-");
        var factory=new SavedData.Factory<Claims>(Claims::new,Claims::load);
        var storage=new DimensionDataStorage(dir.toFile(),DataFixers.getDataFixer(),h.getLevel().registryAccess());
        Claims original=storage.computeIfAbsent(factory,"claims");UUID actor=UUID.randomUUID();original.actor(actor);
        p.getInventory().setItem(0,new ItemStack(Items.ELYTRA));
        h.assertTrue(original.steal(actor,p,0),"elytra taken");original.announce(p.getUUID());storage.save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        var reopened=new DimensionDataStorage(dir.toFile(),DataFixers.getDataFixer(),h.getLevel().registryAccess());
        var unpaid=reopened.computeIfAbsent(factory,"claims");
        h.assertTrue(unpaid.display(p.getUUID()).is(Items.ELYTRA)&&unpaid.announced(p.getUUID()),"unpaid item and banner survive disk reopen");
        h.assertFalse(unpaid.deliver(p,null),"unpaid debt still requires recovery");
        unpaid.settle(p.getUUID());reopened.save();
        net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
        var third=new DimensionDataStorage(dir.toFile(),DataFixers.getDataFixer(),h.getLevel().registryAccess());
        var paid=third.computeIfAbsent(factory,"claims");
        h.assertTrue(paid.deliver(p,null),"prepaid item survives a second disk reopen");
        h.assertFalse(paid.deliver(p,null),"cannot duplicate prepaid return");
        Fixtures.remove(p);h.succeed();
    }
    @GameTest(template="arena",batch="broadcast",timeoutTicks=80)
    public void bothVictimAndBystanderReceiveTheNamedBanner(GameTestHelper h) {
        Fixtures.floor(h);var victim=Fixtures.player(h,4,4);var bystander=Fixtures.player(h,7,7);
        Fixtures.packets(victim);Fixtures.packets(bystander);
        Notices.escaped(h.getLevel().getServer(),victim,new ItemStack(Items.DIAMOND));
        h.runAtTickTime(3,()->{
            for(var player:java.util.List.of(victim,bystander)) {
                var packets=Fixtures.packets(player);
                h.assertTrue(packets.stream().anyMatch(ClientboundSetTitleTextPacket.class::isInstance),"title reaches "+player.getName().getString());
                h.assertTrue(packets.stream().anyMatch(packet->packet instanceof ClientboundSetSubtitleTextPacket p
                        &&p.text().getString().contains(victim.getName().getString())),"subtitle names victim for every recipient");
            }
            Fixtures.remove(victim);Fixtures.remove(bystander);h.succeed();
        });
    }
    @GameTest(template="field",batch="navigation",timeoutTicks=1200)
    public void scheduledVisitWalksUpStealsAndEscapesThroughItsRealAI(GameTestHelper h) {
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)h.setBlock(new BlockPos(x,1,z),Blocks.STONE);
        var p=Fixtures.player(h,48,48);p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,2));
        var old=Visits.active(p.server);if(old!=null)old.park();
        var s=Visits.visit(p);h.assertTrue(s!=null,"natural site selection finds a real site");
        h.assertTrue(Visits.visit(p)==null,"one visit at a time");
        double start=s.distanceTo(p);
        CompoundTag tag=new CompoundTag();s.save(tag);tag.putInt("StealAfter",20);s.load(tag);
        h.runAtTickTime(80,()->h.assertTrue(s.distanceTo(p)<start-2,"navigation approaches the fixed victim"));
        // The escape scenario requires a victim who stops watching. A fixed compass
        // bearing does not guarantee that when vanilla's random flee path curves.
        h.onEachTick(()->{
            if(s.stage()==Encounter.Stage.CHASE) {
                float away=(float)Math.toDegrees(Math.atan2(s.getX()-p.getX(),p.getZ()-s.getZ()));
                p.setYRot(away);p.setYHeadRot(away);p.setXRot(0);
            }
        });
        h.runAtTickTime(1199,()->{
            var state=new CompoundTag();s.save(state);
            com.mojang.logging.LogUtils.getLogger().info("Navigation timeout diagnosis: stage={}, distance={}, removed={}, position={}, age={}, unseen={}, below={}",
                    s.stage(),s.distanceTo(p),s.isRemoved(),s.position(),state.getInt("PrankAge"),state.getInt("Unseen"),
                    h.getLevel().getBlockState(s.blockPosition().below()));
        });
        h.succeedWhen(()->{
            h.assertTrue(s.stage()==Encounter.Stage.CHASE||s.stage()==Encounter.Stage.RANSOM,"AI reaches the inventory and steals");
            h.assertValueEqual(p.getInventory().getItem(0).getCount(),1,"AI takes one item");
            h.assertTrue(s.stage()==Encounter.Stage.RANSOM,"fair chase and sustained loss of sight reach ransom");
            h.assertTrue(Claims.get(p.server).announced(p.getUUID()),"escape is publicly announced");
            s.park();Claims.get(p.server).settle(p.getUUID());Claims.get(p.server).deliver(p,null);Fixtures.remove(p);
        });
    }
}
