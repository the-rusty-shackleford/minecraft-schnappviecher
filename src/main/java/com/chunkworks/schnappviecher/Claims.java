/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;

import com.chunkworks.schnappviecher.domain.VisitSchedule;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The world's confiscated property, kept in overworld SavedData.
 * AF: each victim has at most one item awaiting ransom or delivery; visits records
 * cooldowns; actor is the sole current creature's UUID. RI: stored stacks are
 * private, nonempty, count one; only the server thread mutates; display copies
 * cannot grant ownership. Every mutation marks this data dirty.
 */
public final class Claims extends SavedData {
    private record Claim(ItemStack stack, boolean ready, boolean announced) {}
    private final Map<UUID,Claim> debts = new HashMap<>();
    private final Map<UUID,Long> visits = new HashMap<>();
    @Nullable private UUID actor;
    private long nextVisit;

    /** requires: server thread; effects: loads/creates the single world ledger; throws: data storage errors. */
    public static Claims get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Claims::new,Claims::load),Schnappviecher.ID);
    }
    /** requires: saved NBT and matching registry provider; effects: reconstructs claims; throws: corrupt item errors. */
    public static Claims load(CompoundTag tag, HolderLookup.Provider registries) {
        Claims result=new Claims();
        result.nextVisit=tag.getLong("NextVisit");
        if (tag.hasUUID("Actor")) result.actor=tag.getUUID("Actor");
        for (Tag row : tag.getList("Claims",Tag.TAG_COMPOUND)) {
            CompoundTag c=(CompoundTag)row;
            ItemStack item=ItemStack.parseOptional(registries,c.getCompound("Item"));
            if (!item.isEmpty() && c.hasUUID("Victim")) result.debts.put(c.getUUID("Victim"),
                    new Claim(item.copyWithCount(1),c.getBoolean("Ready"),c.getBoolean("Announced")));
        }
        for (Tag row : tag.getList("Visits",Tag.TAG_COMPOUND)) {
            CompoundTag c=(CompoundTag)row;
            if(c.hasUUID("Player")) result.visits.put(c.getUUID("Player"),Math.max(0,c.getLong("At")));
        }
        return result;
    }
    /** requires: server thread; effects: serializes without exposing the stored stacks; throws: registry encoding failures. */
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong("NextVisit",nextVisit);
        if(actor!=null) tag.putUUID("Actor",actor);
        ListTag rows=new ListTag();
        debts.forEach((id,claim)->{
            CompoundTag c=new CompoundTag(); c.putUUID("Victim",id);
            c.put("Item",claim.stack().save(registries)); c.putBoolean("Ready",claim.ready());
            c.putBoolean("Announced",claim.announced()); rows.add(c);
        }); tag.put("Claims",rows);
        ListTag history=new ListTag();
        visits.forEach((id,time)->{CompoundTag c=new CompoundTag();c.putUUID("Player",id);c.putLong("At",time);history.add(c);});
        tag.put("Visits",history); return tag;
    }
    /** requires: none; effects: reports whether id owns the current encounter; throws: none. */
    public boolean isActor(UUID id) { return id.equals(actor); }
    /** requires: none; effects: returns the immutable active UUID, if any; throws: none. */
    @Nullable public UUID actor() { return actor; }
    /** requires: server thread; effects: replaces the active actor, invalidating stale copies; throws: none. */
    public void actor(@Nullable UUID id) { actor=id; setDirty(); }
    /** requires: none; effects: reports an outstanding item, including prepaid returns; throws: none. */
    public boolean owes(UUID victim) { return debts.containsKey(victim); }
    /** requires: none; effects: returns a defensive display copy; throws: none. */
    public ItemStack display(UUID victim) { Claim c=debts.get(victim);return c==null?ItemStack.EMPTY:c.stack().copy(); }
    /** requires: server thread, actual player's inventory; effects: atomically moves one eligible item into the ledger; throws: none. */
    public boolean steal(UUID creature, Player victim, int slot) {
        if(!isActor(creature)||owes(victim.getUUID())||victim.isCreative()||victim.isSpectator()
                ||slot<0||slot>=victim.getInventory().getContainerSize()) return false;
        ItemStack source=victim.getInventory().getItem(slot);
        if(source.isEmpty()) return false;
        ItemStack item=source.split(1);
        debts.put(victim.getUUID(),new Claim(item,false,false));
        victim.getInventory().setChanged(); victim.containerMenu.broadcastChanges(); setDirty(); return true;
    }
    /** requires: server thread; effects: marks one escape announcement, returning false for repeats or absent claims; throws: none. */
    public boolean announce(UUID victim) {
        Claim c=debts.get(victim); if(c==null||c.announced()||c.ready())return false;
        debts.put(victim,new Claim(c.stack(),false,true));setDirty();return true;
    }
    /** requires: none; effects: reports whether the chase already ended publicly; throws: none. */
    public boolean announced(UUID victim) { Claim c=debts.get(victim);return c!=null&&c.announced(); }
    /** requires: server thread; effects: makes the item returnable without another payment; throws: none. */
    public void settle(UUID victim) {
        Claim c=debts.get(victim);if(c!=null){debts.put(victim,new Claim(c.stack(),true,c.announced()));setDirty();}
    }
    /**
     * requires: server thread; effects: places a prepaid item in the owner's world,
     * protected from damage and expiry and reserved for their pickup. Removes the
     * claim only when the real level accepts the drop. throws: none.
     */
    public boolean deliver(ServerPlayer owner, @Nullable Entity mouth) {
        Claim c=debts.get(owner.getUUID());if(c==null||!c.ready())return false;
        boolean nearby=mouth!=null&&mouth.level()==owner.level()&&mouth.distanceToSqr(owner)<1024;
        ItemEntity drop=new ItemEntity(owner.serverLevel(),nearby?mouth.getX():owner.getX(),
                nearby?mouth.getY()+2.4:owner.getY()+.6,nearby?mouth.getZ():owner.getZ(),c.stack().copy());
        drop.setTarget(owner.getUUID());drop.setInvulnerable(true);drop.setUnlimitedLifetime();
        drop.setDefaultPickUpDelay();
        if(nearby)drop.setDeltaMovement(owner.position().subtract(drop.position()).normalize().scale(.22).add(0,.16,0));
        if(!owner.serverLevel().addFreshEntity(drop))return false;
        debts.remove(owner.getUUID());setDirty();return true;
    }
    /** requires: nonnegative game time; effects: tests the user's configured player cooldown; throws: invalid time exceptions. */
    public boolean eligible(Player player,long now) {
        return VisitSchedule.eligible(now,visits.getOrDefault(player.getUUID(),-1L),PrankConfig.COOLDOWN_MINUTES.get()*1200L,
                player.isAlive()&&!player.isSpectator()&&!player.isCreative(),owes(player.getUUID()));
    }
    /** requires: nonnegative game time; effects: remembers the targeted player; throws: none. */
    public void visited(UUID player,long now) { visits.put(player,now);setDirty(); }
    /** requires: none; effects: returns the next natural visit time; throws: none. */
    public long nextVisit() { return nextVisit; }
    /** requires: nonnegative game time; effects: schedules the next visit; throws: none. */
    public void nextVisit(long time) { nextVisit=Math.max(0,time);setDirty(); }
}
