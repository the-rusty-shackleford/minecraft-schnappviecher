/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.schnappviecher;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry identities, immutable after registration. */
public final class Content {
    private Content() {}
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,Schnappviecher.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,Schnappviecher.ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,Schnappviecher.ID);
    public static final DeferredHolder<EntityType<?>,EntityType<Schnappviech>> CREATURE = ENTITIES.register("schnappviech",()->
            EntityType.Builder.of(Schnappviech::new,MobCategory.CREATURE).sized(1.1f,3.5f).eyeHeight(2.9f)
                    .fireImmune().clientTrackingRange(12).updateInterval(2).build("schnappviecher:schnappviech"));
    public static final DeferredHolder<Item,Item> EGG = ITEMS.register("schnappviech_spawn_egg",()->
            new DeferredSpawnEggItem(CREATURE,0x695345,0xd7c593,new Item.Properties()));
    public static final DeferredHolder<SoundEvent,SoundEvent> GIGGLE = sound("giggle");
    public static final DeferredHolder<SoundEvent,SoundEvent> CLACK = sound("clack");
    public static final DeferredHolder<SoundEvent,SoundEvent> YELP = sound("yelp");
    public static final TagKey<Item> PAYMENT = TagKey.create(Registries.ITEM,id("ransom_snacks"));
    private static DeferredHolder<SoundEvent,SoundEvent> sound(String name) {
        return SOUNDS.register(name,()->SoundEvent.createVariableRangeEvent(id(name)));
    }
    /** requires: valid resource path; effects: returns this mod's resource id; throws: invalid path exception. */
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(Schnappviecher.ID,path); }
    static void register(IEventBus bus) {
        ENTITIES.register(bus); ITEMS.register(bus); SOUNDS.register(bus);
        bus.addListener(Content::attributes); bus.addListener(Content::tabs);
    }
    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(CREATURE.get(), Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20)
                .add(Attributes.MOVEMENT_SPEED,.32).add(Attributes.FOLLOW_RANGE,48)
                .add(Attributes.STEP_HEIGHT,1).add(Attributes.KNOCKBACK_RESISTANCE,1).build());
    }
    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) event.accept(EGG.get());
    }
}
