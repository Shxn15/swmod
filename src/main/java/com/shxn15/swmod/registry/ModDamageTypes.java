package com.shxn15.swmod.registry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import com.shxn15.swmod.SwMod;

/**
 * Schadensarten des Mods. Die eigentlichen Werte stehen datengetrieben in
 * {@code data/swmod/damage_type/*.json}.
 */
public final class ModDamageTypes {
	public static final ResourceKey<DamageType> LIGHTSABER = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("lightsaber"));
	public static final ResourceKey<DamageType> THROWN_LIGHTSABER = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("thrown_lightsaber"));

	private ModDamageTypes() {
	}

	public static DamageSource lightsaber(Level level, Entity attacker) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LIGHTSABER), attacker);
	}

	public static DamageSource thrownLightsaber(Level level, Entity projectile, @Nullable Entity owner) {
		return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(THROWN_LIGHTSABER), projectile, owner);
	}

	public static boolean isLightsaber(DamageSource source) {
		return source.is(LIGHTSABER) || source.is(THROWN_LIGHTSABER);
	}
}
