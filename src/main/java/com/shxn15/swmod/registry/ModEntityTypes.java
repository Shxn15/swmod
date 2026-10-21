package com.shxn15.swmod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;

public final class ModEntityTypes {
	public static final ResourceKey<EntityType<?>> THROWN_LIGHTSABER_KEY = ResourceKey.create(Registries.ENTITY_TYPE, SwMod.id("thrown_lightsaber"));

	public static final EntityType<ThrownLightsaberEntity> THROWN_LIGHTSABER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			THROWN_LIGHTSABER_KEY,
			EntityType.Builder.<ThrownLightsaberEntity>of(ThrownLightsaberEntity::new, MobCategory.MISC)
					.sized(0.5f, 0.5f)
					.clientTrackingRange(8)
					.updateInterval(1)
					.noSave()
					.build(THROWN_LIGHTSABER_KEY));

	private ModEntityTypes() {
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
