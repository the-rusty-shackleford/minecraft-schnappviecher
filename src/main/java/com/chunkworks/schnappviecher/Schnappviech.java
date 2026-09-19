/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;

import com.chunkworks.schnappviecher.domain.Encounter;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * An immortal, ground-walking prankster. AF: encounter is its current performance,
 * victim fixes one player's identity, and the world ledger owns any stolen item.
 * RI: only the ledger's actor may steal/settle/announce; client stack is display
 * only; no player damage, death loot, block breaking, or forced chunk loading.
 */
public final class Schnappviech extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STAGE=SynchedEntityData.defineId(Schnappviech.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> HELD=SynchedEntityData.defineId(Schnappviech.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> WATCHED=SynchedEntityData.defineId(Schnappviech.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> JAW=SynchedEntityData.defineId(Schnappviech.class,EntityDataSerializers.INT);
    private Encounter encounter=Encounter.start();
    @Nullable private UUID victim;
    private int stealAfter;
    private int giggleIn=80;
    private int absent;
    private int reaction;

    /** requires: registered type and level; effects: creates an unassigned creature; throws: none. */
    public Schnappviech(EntityType<? extends Schnappviech> type,Level level) {
        super(type,level);setPersistenceRequired();xpReward=0;
    }
    @Override protected void registerGoals() { goalSelector.addGoal(0,new FloatGoal(this)); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);b.define(STAGE,0);b.define(HELD,ItemStack.EMPTY);b.define(WATCHED,false);b.define(JAW,0);
    }
    /** requires: server thread and player in this level; effects: fixes a target and adopts their outstanding claim; throws: IllegalStateException if another live actor exists. */
    public void begin(Player player) {
        if(level().isClientSide)return;
        Claims claims=claims();
        Schnappviech current=Visits.active(level().getServer());
        if(current!=null&&current!=this)throw new IllegalStateException("a prank is already active");
        victim=player.getUUID();claims.actor(getUUID());
        stealAfter=PrankConfig.STALK_TICKS.get()+random.nextInt(1201);
        encounter=claims.owes(victim)?new Encounter(claims.announced(victim)?Encounter.Stage.RANSOM:Encounter.Stage.CHASE,0,0,0,0):Encounter.start();
        claims.visited(victim,level().getServer().overworld().getGameTime());sync();
    }
    private Claims claims() { return Claims.get(level().getServer()); }
    /** requires: none; effects: returns the synchronized stage; throws: none. */
    public Encounter.Stage stage() { return Encounter.Stage.values()[entityData.get(STAGE)]; }
    /** requires: none; effects: returns a defensive held-item copy; throws: none. */
    public ItemStack heldItem() { return entityData.get(HELD).copy(); }
    /** requires: none; effects: tests held-item presence without copying components; throws: none. */
    public boolean hasHeldItem() { return !entityData.get(HELD).isEmpty(); }
    /** requires: none; effects: reports whether the victim is looking at this creature; throws: none. */
    public boolean watched() { return entityData.get(WATCHED); }
    /** requires: none; effects: returns the remaining jaw animation ticks; throws: none. */
    public int jawTicks() { return entityData.get(JAW); }
    /** requires: none; effects: returns remaining client reaction ticks; throws: none. */
    public int reactionTicks() { return reaction; }
    private void sync() {
        entityData.set(STAGE,encounter.stage().ordinal());
        ItemStack held=victim==null?ItemStack.EMPTY:claims().display(victim);
        if(!ItemStack.matches(held,entityData.get(HELD)))entityData.set(HELD,held);
    }
    @Override public void tick() {
        super.tick();if(reaction>0)reaction--;
        if(!level().isClientSide&&entityData.get(JAW)>0)entityData.set(JAW,entityData.get(JAW)-1);
    }
    @Override protected void customServerAiStep() {
        super.customServerAiStep();
        if(victim==null) {
            Player nearest=level().getNearestPlayer(this,24);
            if(nearest!=null&&Visits.active(level().getServer())==null)begin(nearest);
            else if(tickCount>200)discard();
            return;
        }
        if(!claims().isActor(getUUID())){discard();return;}
        Player player=level().getPlayerByUUID(victim);
        if(player==null||!player.isAlive()||player.isSpectator()||getY()<level().getMinBuildHeight()-8) {
            if(++absent>40)park();return;
        }
        absent=0;
        boolean seen=isWatchedBy(player);entityData.set(WATCHED,seen);
        encounter=encounter.tick(seen);
        getLookControl().setLookAt(player,25,15);
        if(--giggleIn<=0&&encounter.stage()!=Encounter.Stage.RETREAT) {
            giggleIn=160+random.nextInt(241);entityData.set(JAW,28);
            playSound(Content.GIGGLE.get(),.85f,.94f+random.nextFloat()*.12f);
        }
        switch(encounter.stage()) {
            case STALK -> stalk(player,seen);
            case CHASE -> chase(player);
            case RANSOM -> ransom(player);
            case RETREAT -> { if(tickCount%10==0)flee(player,1.35);if(encounter.age()>100)park(); }
        }
        sync();
    }
    private boolean isWatchedBy(Player player) {
        Vec3 delta=getEyePosition().subtract(player.getEyePosition());
        return delta.lengthSqr()<1||player.getLookAngle().dot(delta.normalize())>.65&&player.hasLineOfSight(this);
    }
    private void stalk(Player player,boolean seen) {
        if(player.isCreative()){encounter=encounter.enter(Encounter.Stage.RETREAT);return;}
        if(encounter.canSteal(stealAfter,seen,hasLineOfSight(player),distanceTo(player))&&trySteal(player))return;
        if(encounter.age()>stealAfter+3600){encounter=encounter.enter(Encounter.Stage.RETREAT);return;}
        if(seen){navigation.stop();return;}
        if(tickCount%10==0) {
            Vec3 look=player.getLookAngle();
            double standOff=encounter.age()<stealAfter?5:1.2;
            navigation.moveTo(player.getX()-look.x*standOff,player.getY(),player.getZ()-look.z*standOff,.72);
        }
    }
    /** requires: server thread; effects: attempts one eligible theft through the real inventory path; throws: none. */
    public boolean trySteal(Player player) {
        if(victim==null||!victim.equals(player.getUUID())||encounter.stage()!=Encounter.Stage.STALK
                ||distanceTo(player)>2.4||!hasLineOfSight(player)||isWatchedBy(player))return false;
        int slot=-1,eligible=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            if(!player.getInventory().getItem(i).isEmpty()&&random.nextInt(++eligible)==0)slot=i;
        }
        if(!claims().steal(getUUID(),player,slot))return false;
        encounter=encounter.stolen();entityData.set(JAW,18);sync();
        playSound(Content.CLACK.get(),1,.9f);flee(player,1.4);return true;
    }
    private void chase(Player player) {
        if(!claims().owes(victim)){encounter=encounter.enter(Encounter.Stage.RETREAT);return;}
        if(tickCount%10==0)flee(player,1.35);
        if(encounter.canEscape(PrankConfig.CHASE_TICKS.get(),distanceTo(player))) {
            encounter=encounter.enter(Encounter.Stage.RANSOM);navigation.stop();
            if(claims().announce(victim))Notices.escaped(level().getServer(),player,claims().display(victim));
            entityData.set(JAW,36);playSound(Content.GIGGLE.get(),1,1.07f);
        }
    }
    private void ransom(Player player) {
        if(!claims().owes(victim)){encounter=encounter.enter(Encounter.Stage.RETREAT);return;}
        if(tickCount%20==0) {
            if(distanceToSqr(player)>36)navigation.moveTo(player,1);
            else navigation.stop();
        }
        if(encounter.age()>6000)park();
    }
    private void flee(Player player,double speed) {
        Vec3 away=DefaultRandomPos.getPosAway(this,16,4,player.position());
        if(away!=null)navigation.moveTo(away.x,away.y,away.z,speed);
    }
    @Override protected InteractionResult mobInteract(Player player,InteractionHand hand) {
        if(stage()!=Encounter.Stage.RANSOM||heldItem().isEmpty())return InteractionResult.PASS;
        ItemStack snack=player.getItemInHand(hand);
        boolean creative=player.getAbilities().instabuild;
        if(!creative&&!snack.is(Content.PAYMENT)) {
            if(!level().isClientSide)player.displayClientMessage(Component.translatable("notice.schnappviecher.fee"),true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if(!level().isClientSide&&victim!=null&&claims().isActor(getUUID())&&claims().owes(victim)) {
            claims().settle(victim);if(!creative)snack.shrink(1);
            player.getInventory().setChanged();player.containerMenu.broadcastChanges();
            returnProperty();encounter=encounter.enter(Encounter.Stage.RETREAT);sync();entityData.set(JAW,32);
            playSound(SoundEvents.GENERIC_EAT,.9f,.65f);
            player.displayClientMessage(Component.translatable("notice.schnappviecher.receipt",player.getDisplayName()),false);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    private void returnProperty() {
        if(victim==null)return;
        ServerPlayer owner=level().getServer().getPlayerList().getPlayer(victim);
        if(owner==null&&level().getPlayerByUUID(victim) instanceof ServerPlayer testPlayer)owner=testPlayer;
        if(owner!=null)claims().deliver(owner,this);
    }
    @Override public boolean hurt(DamageSource source,float amount) {
        if(level().isClientSide)return true;
        if(!(source.getEntity() instanceof Player)||amount<=0||victim==null||!claims().isActor(getUUID()))return false;
        Encounter hit=encounter.hit();if(hit==encounter)return false;
        encounter=hit;level().broadcastEntityEvent(this,(byte)4);playSound(Content.YELP.get(),.9f,1);
        if(encounter.stage()==Encounter.Stage.RETREAT){claims().settle(victim);returnProperty();encounter=encounter.enter(Encounter.Stage.RETREAT);}
        sync();return true;
    }
    @Override public void handleEntityEvent(byte event) {
        if(event==4)reaction=10;else super.handleEntityEvent(event);
    }
    /** requires: server thread; effects: leaves without destroying any outstanding property; throws: none. */
    public void park() {
        if(!level().isClientSide&&claims().isActor(getUUID())) {
            claims().actor(null);long soon=level().getServer().overworld().getGameTime()+1200;
            claims().nextVisit(Math.min(claims().nextVisit(),soon));
            ((ServerLevel)level()).sendParticles(ParticleTypes.POOF,getX(),getY()+1.5,getZ(),12,.5,.8,.5,.03);
        }discard();
    }
    @Override public boolean doHurtTarget(Entity entity) { return false; }
    @Override public void kill() { /* Immortal. Operators can safely park it with /schnappviecher dismiss. */ }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean canChangeDimensions(Level from,Level to) { return false; }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);if(victim!=null)tag.putUUID("Victim",victim);
        tag.putString("PrankStage",encounter.stage().name());tag.putInt("PrankAge",encounter.age());
        tag.putInt("Blows",encounter.blows());tag.putInt("Immunity",encounter.immunity());tag.putInt("Unseen",encounter.unseen());
        tag.putInt("StealAfter",stealAfter);tag.putInt("GiggleIn",giggleIn);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);victim=tag.hasUUID("Victim")?tag.getUUID("Victim"):null;
        try{encounter=new Encounter(Encounter.Stage.valueOf(tag.getString("PrankStage")),Math.max(0,tag.getInt("PrankAge")),
                Math.clamp(tag.getInt("Blows"),0,3),Math.max(0,tag.getInt("Immunity")),Math.max(0,tag.getInt("Unseen")));}
        catch(IllegalArgumentException e){encounter=Encounter.start();}
        stealAfter=Math.max(1,tag.getInt("StealAfter"));giggleIn=Math.max(20,tag.getInt("GiggleIn"));
        if(!level().isClientSide)sync();
    }
}
