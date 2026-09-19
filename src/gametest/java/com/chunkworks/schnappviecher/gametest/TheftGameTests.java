/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher.gametest;

import com.chunkworks.schnappviecher.*;
import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

/**
 * Partitions: hotbar/main/armor/offhand; stack/tool/modded/container; absent/stale
 * actor; watched/behind/wall; normal/rapid/spaced/environmental hits; survival vs
 * creative payment; owner/friend; empty/loaded claim; unpaid/prepaid save reload;
 * offline owner/reconnection; full inventory/owner-only pickup; all three snacks.
 * These use real inventories, ItemStack codecs, entities and damage/interaction paths.
 */
@GameTestHolder("schnappviecher")
@PrefixGameTestTemplate(false)
public final class TheftGameTests {
    @GameTest(template="arena",batch="claims")
    public void everyCarriedSlotAndFullItemComponentsSurviveASave(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,3,3);
        int[] slots={0,9,35,36,39,40};
        for(int slot:slots) {
            Claims claims=new Claims();UUID actor=UUID.randomUUID();claims.actor(actor);
            ItemStack box=new ItemStack(Items.SHULKER_BOX);
            box.set(DataComponents.CUSTOM_NAME,Component.literal("The lunch department"));
            box.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,64),new ItemStack(Content.EGG.get()))));
            p.getInventory().setItem(slot,box.copy());
            h.assertTrue(claims.steal(actor,p,slot),"slot "+slot+" is stealable");
            h.assertTrue(p.getInventory().getItem(slot).isEmpty(),"exact source removed");
            var loaded=Claims.load(claims.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(ItemStack.matches(box,loaded.display(p.getUUID())),"all components survive codec roundtrip");
            var copy=loaded.display(p.getUUID());copy.setCount(0);
            h.assertFalse(loaded.display(p.getUUID()).isEmpty(),"display cannot mutate claim");
        }
        ItemStack sword=new ItemStack(Items.NETHERITE_SWORD);sword.setDamageValue(137);
        sword.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),5);
        sword.set(DataComponents.CUSTOM_NAME,Component.literal("Definitely not a snack"));
        Claims claims=new Claims();UUID actor=UUID.randomUUID();claims.actor(actor);p.getInventory().setItem(0,sword.copy());
        h.assertTrue(claims.steal(actor,p,0),"tools allowed");
        Claims savedSword=Claims.load(claims.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(ItemStack.matches(sword,savedSword.display(p.getUUID())),"damage, name and enchantment retained through save");
        Fixtures.remove(p);h.succeed();
    }
    @GameTest(template="arena",batch="claims")
    public void oneItemOneClaimAndOnlyTheAuthorizedActor(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,4,3);Claims claims=new Claims();UUID a=UUID.randomUUID();claims.actor(a);
        p.getInventory().setItem(0,new ItemStack(Items.DIAMOND,64));
        h.assertFalse(claims.steal(UUID.randomUUID(),p,0),"stale actor refused");
        h.assertFalse(claims.steal(a,p,41),"invalid slot refused");
        h.assertTrue(claims.steal(a,p,0),"authorized theft");
        h.assertValueEqual(p.getInventory().getItem(0).getCount(),63,"takes one");
        h.assertFalse(claims.steal(a,p,0),"cannot steal twice");
        h.assertTrue(claims.announce(p.getUUID()),"first escape announces");
        h.assertFalse(claims.announce(p.getUUID()),"no repeat banner");
        claims.settle(p.getUUID());Claims loaded=Claims.load(claims.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.deliver(p,null),"prepaid return survives reload");
        h.assertFalse(loaded.deliver(p,null),"delivery exactly once");
        Fixtures.remove(p);h.succeed();
    }
    @GameTest(template="arena",batch="combat",timeoutTicks=160)
    public void realPlayerHitsRecoverTheItemWithoutKilling(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,12,12);var s=creature(h,p);
        p.getInventory().setItem(0,new ItemStack(Items.EMERALD,7));
        h.assertTrue(s.trySteal(p),"steals from behind");s.setNoAi(true);
        h.assertValueEqual(p.getInventory().getItem(0).getCount(),6,"one missing");
        float health=s.getHealth();
        s.hurt(s.damageSources().lava(),1000);s.hurt(s.damageSources().fellOutOfWorld(),1000);s.kill();
        h.assertTrue(s.isAlive()&&!s.isRemoved(),"environment and kill cannot kill it");
        h.assertValueEqual(s.getHealth(),health,"immortal health");
        // Tick the actual AI so the hit window advances; a wall keeps the actor near the fixture.
        s.setNoAi(false);p.attack(s);p.attack(s);
        h.assertFalse(s.heldItem().isEmpty(),"rapid hits do not surrender");
        h.runAtTickTime(12,()->p.attack(s));
        h.runAtTickTime(24,()->p.attack(s));
        h.runAtTickTime(25,()->{
            h.assertTrue(s.heldItem().isEmpty(),"third spaced hit releases property");
            h.assertTrue(s.isAlive(),"still alive");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(Items.EMERALD));
            h.assertTrue(drops.size()==1&&drops.getFirst().getItem().getCount()==1,"one real drop");
            s.park();Fixtures.remove(p);h.succeed();
        });
    }
    @GameTest(template="arena",batch="payment",timeoutTicks=160)
    public void friendCanPayExactlyOneSnackAndCreativeNeedsNothing(GameTestHelper h) {
        Fixtures.floor(h);var owner=Fixtures.player(h,12,12);var friend=Fixtures.player(h,15,12);
        var s=creature(h,owner);owner.getInventory().setItem(0,new ItemStack(Content.EGG.get()));
        h.assertTrue(s.trySteal(owner),"modded items allowed");
        forceRansom(s);
        friend.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));friend.interactOn(s,InteractionHand.MAIN_HAND);
        h.assertFalse(s.heldItem().isEmpty(),"wrong fee changes nothing");
        friend.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.COOKIE,3));friend.interactOn(s,InteractionHand.MAIN_HAND);
        h.assertValueEqual(friend.getMainHandItem().getCount(),2,"one snack consumed");
        h.assertTrue(s.heldItem().isEmpty(),"friend freed property");
        friend.interactOn(s,InteractionHand.MAIN_HAND);
        h.assertValueEqual(friend.getMainHandItem().getCount(),2,"repeated interaction does not charge twice");
        s.park();
        var second=creature(h,owner);owner.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        h.assertTrue(second.trySteal(owner),"new encounter");forceRansom(second);
        friend.setGameMode(GameType.CREATIVE);friend.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        friend.interactOn(second,InteractionHand.MAIN_HAND);
        h.assertTrue(second.heldItem().isEmpty(),"creative needs no payment item");
        second.park();Fixtures.remove(owner);Fixtures.remove(friend);h.succeed();
    }
    @GameTest(template="arena",batch="approach",timeoutTicks=100)
    public void lookingAndSolidWallsPreventTheft(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,12,12);var s=creature(h,p);
        p.getInventory().setItem(0,new ItemStack(Items.DIAMOND));
        p.setYRot(180);p.setYHeadRot(180);p.setXRot(-35);
        h.assertFalse(s.trySteal(p),"cannot steal under direct observation");
        p.setYRot(0);p.setYHeadRot(0);p.setXRot(0);
        for(int y=2;y<7;y++)for(int x=11;x<=13;x++)h.setBlock(new BlockPos(x,y,11),Blocks.STONE);
        h.assertFalse(s.trySteal(p),"cannot reach through wall");
        for(int y=2;y<7;y++)for(int x=11;x<=13;x++)h.setBlock(new BlockPos(x,y,11),Blocks.AIR);
        h.assertTrue(s.trySteal(p),"unwatched open approach works");
        s.park();Claims.get(h.getLevel().getServer()).settle(p.getUUID());Claims.get(h.getLevel().getServer()).deliver(p,null);
        Fixtures.remove(p);h.succeed();
    }
    @GameTest(template="arena",batch="reload",timeoutTicks=100)
    public void replacementCarriesTheSameDebtAndStaleActorsCannotDuplicateIt(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,12,12);var s=creature(h,p);
        p.getInventory().setItem(0,new ItemStack(Items.NETHERITE_HELMET));
        h.assertTrue(s.trySteal(p),"theft recorded");
        var tag=new CompoundTag();s.save(tag);s.park();
        var replacement=creature(h,p);
        h.assertTrue(replacement.heldItem().is(Items.NETHERITE_HELMET),"replacement finds outstanding item");
        var stale=Content.CREATURE.get().create(h.getLevel());stale.load(tag);stale.setUUID(UUID.randomUUID());h.getLevel().addFreshEntity(stale);
        h.runAtTickTime(3,()->{
            h.assertTrue(stale.isRemoved(),"stale actor leaves");
            h.assertFalse(replacement.heldItem().isEmpty(),"replacement still owns claim");
            replacement.park();Claims.get(h.getLevel().getServer()).settle(p.getUUID());Claims.get(h.getLevel().getServer()).deliver(p,null);
            Fixtures.remove(p);h.succeed();
        });
    }
    @GameTest(template="arena",batch="offline",timeoutTicks=100)
    public void offlinePaymentReturnsAfterReconnectAndOnlyTheOwnerCanCollect(GameTestHelper h) {
        Fixtures.floor(h);var owner=Fixtures.player(h,12,12);var friend=Fixtures.player(h,15,12);
        var profile=owner.getGameProfile();var s=creature(h,owner);
        owner.getInventory().setItem(0,new ItemStack(Items.DIAMOND));h.assertTrue(s.trySteal(owner),"theft");forceRansom(s);
        Fixtures.remove(owner);
        friend.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.BREAD,2));friend.interactOn(s,InteractionHand.MAIN_HAND);
        h.assertValueEqual(friend.getMainHandItem().getCount(),1,"friend pays one bread while owner is offline");
        h.assertTrue(Claims.get(friend.server).owes(profile.getId()),"offline return is retained");s.park();
        var returned=Fixtures.player(h,12,12,profile);
        for(int slot=0;slot<36;slot++)returned.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
        h.succeedWhen(()->{
            h.assertFalse(Claims.get(returned.server).owes(returned.getUUID()),"scheduler delivers on reconnect");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(Items.DIAMOND));
            h.assertValueEqual(drops.size(),1,"exactly one return");var drop=drops.getFirst();drop.setPickUpDelay(0);
            drop.playerTouch(friend);h.assertFalse(drop.isRemoved(),"bystander cannot collect another player's property");
            drop.playerTouch(returned);h.assertFalse(drop.isRemoved(),"full inventory leaves recoverable drop");
            returned.getInventory().setItem(0,ItemStack.EMPTY);drop.playerTouch(returned);
            h.assertTrue(drop.isRemoved()&&returned.getInventory().getItem(0).is(Items.DIAMOND),"owner collects exact returned item");
            h.assertTrue(drop.isInvulnerable(),"return is protected from ordinary damage");
            Fixtures.remove(returned);Fixtures.remove(friend);
        });
    }
    @GameTest(template="arena",batch="eligibility")
    public void creativeAndSpectatorCannotBeRobbedAndApplePaysTheRansom(GameTestHelper h) {
        Fixtures.floor(h);var p=Fixtures.player(h,12,12);var claims=Claims.get(p.server);UUID actor=UUID.randomUUID();claims.actor(actor);
        p.getInventory().setItem(0,new ItemStack(Items.DIAMOND));p.setGameMode(GameType.CREATIVE);
        h.assertFalse(claims.steal(actor,p,0),"creative cannot be robbed");p.setGameMode(GameType.SPECTATOR);
        h.assertFalse(claims.steal(actor,p,0),"spectator cannot be robbed");p.setGameMode(GameType.SURVIVAL);
        h.assertFalse(claims.steal(actor,p,1),"empty inventory slot cannot be robbed");
        var s=creature(h,p);h.assertTrue(s.trySteal(p),"survival can be robbed");forceRansom(s);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.APPLE,2));p.interactOn(s,InteractionHand.MAIN_HAND);
        h.assertValueEqual(p.getMainHandItem().getCount(),1,"one apple paid");h.assertFalse(claims.owes(p.getUUID()),"apple settles claim");
        s.park();Fixtures.remove(p);h.succeed();
    }
    private static Schnappviech creature(GameTestHelper h,ServerPlayer target) {
        var active=Visits.active(h.getLevel().getServer());if(active!=null)active.park();
        var s=Content.CREATURE.get().create(h.getLevel());
        s.moveTo(target.getX(),target.getY(),target.getZ()-2,0,0);h.getLevel().addFreshEntity(s);s.begin(target);return s;
    }
    // Saved state is a real supported input: resumes exactly where an escaping actor was saved.
    private static void forceRansom(Schnappviech s) {
        Claims.get(s.level().getServer()).announce(s.level().getNearestPlayer(s,8).getUUID());
        CompoundTag tag=new CompoundTag();s.save(tag);tag.putString("PrankStage",Encounter.Stage.RANSOM.name());s.load(tag);
    }
}
