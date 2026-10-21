package com.shxn15.swmod.registry;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import com.shxn15.swmod.SwMod;

/**
 * Schadensarten des Mods. Die eigentlichen Werte stehen datengetrieben in
 * {@code data/swmod/damage_type/*.json}.
 */
public final class ModDamageTypes {
	public static final ResourceKey<DamageType> LIGHTSABER = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("lightsaber"));
	public static final ResourceKey<DamageType> THROWN_LIGHTSABER = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("thrown_lightsaber"));
	public static final ResourceKey<DamageType> FORCE = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("force"));
	public static final ResourceKey<DamageType> FORCE_LIGHTNING = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("force_lightning"));
	public static final ResourceKey<DamageType> FORCE_GRIP = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("force_grip"));
	public static final ResourceKey<DamageType> FORCE_IMPACT = ResourceKey.create(Registries.DAMAGE_TYPE, SwMod.id("force_impact"));

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

	/** Machtstoß/-zug: direkter Schaden durch die Wucht der Macht. */
	public static DamageSource force(Level level, Entity attacker) {
		return new DamageSource(type(level, FORCE), attacker);
	}

	public static DamageSource forceLightning(Level level, Entity attacker) {
		return new DamageSource(type(level, FORCE_LIGHTNING), attacker);
	}

	public static DamageSource forceGrip(Level level, Entity attacker) {
		return new DamageSource(type(level, FORCE_GRIP), attacker);
	}

	/** Aufprall an einer Wand nach einem Machtstoß. Ohne Verursacher, wenn dieser nicht mehr da ist. */
	public static DamageSource forceImpact(Level level, @Nullable Entity attacker) {
		return attacker != null ? new DamageSource(type(level, FORCE_IMPACT), attacker) : new DamageSource(type(level, FORCE_IMPACT));
	}

	/** Schaden durch Machtfähigkeiten: das Lichtschwert blockt ihn nicht automatisch. */
	public static boolean isForce(DamageSource source) {
		return source.is(FORCE) || source.is(FORCE_LIGHTNING) || source.is(FORCE_GRIP) || source.is(FORCE_IMPACT);
	}

	/**
	 * Hebt die kurze Trefferpause nach einem Schaden auf, damit schnelle Folgetreffer (Kombos, Blitze,
	 * Würgen) alle zählen. Ohne das schluckt Minecraft jeden Treffer, der innerhalb von 10 Ticks nach
	 * dem letzten kommt und nicht stärker ist.
	 */
	public static void skipHurtCooldown(LivingEntity target) {
		target.damageCooldownTime = 0;
	}

	private static Holder<DamageType> type(Level level, ResourceKey<DamageType> key) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
	}
}
