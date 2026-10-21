package com.shxn15.swmod.force;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.force.ability.GrabAbility;
import com.shxn15.swmod.force.ability.GripAbility;
import com.shxn15.swmod.force.ability.HealAbility;
import com.shxn15.swmod.force.ability.LightningAbility;
import com.shxn15.swmod.force.ability.PullAbility;
import com.shxn15.swmod.force.ability.PushAbility;
import com.shxn15.swmod.force.ability.SpeedAbility;

/**
 * Alle Machtfähigkeiten. Eine neue Fähigkeit braucht nur eine Klasse, einen Eintrag hier
 * (oder {@link #register(Identifier, ForceAbility)} aus einem anderen Mod), ein Symbol und zwei
 * Übersetzungen – Holocron, Rad und HUD finden sie automatisch.
 */
public final class ForceAbilities {
	public static final ResourceKey<Registry<ForceAbility>> REGISTRY_KEY = ResourceKey.createRegistryKey(SwMod.id("force_ability"));
	public static final Registry<ForceAbility> REGISTRY = FabricRegistryBuilder.create(REGISTRY_KEY)
			.attribute(RegistryAttribute.SYNCED)
			.buildAndRegister();

	public static final ForceAbility PUSH = register("push", new PushAbility());
	public static final ForceAbility PULL = register("pull", new PullAbility());
	public static final ForceAbility GRAB = register("grab", new GrabAbility());
	public static final ForceAbility SPEED = register("speed", new SpeedAbility());
	public static final ForceAbility HEAL = register("heal", new HealAbility());
	public static final ForceAbility LIGHTNING = register("lightning", new LightningAbility());
	public static final ForceAbility GRIP = register("grip", new GripAbility());

	private ForceAbilities() {
	}

	private static <T extends ForceAbility> T register(String name, T ability) {
		return register(SwMod.id(name), ability);
	}

	public static <T extends ForceAbility> T register(Identifier id, T ability) {
		return Registry.register(REGISTRY, id, ability);
	}

	/** Alle Fähigkeiten in Registrierungsreihenfolge. */
	public static List<ForceAbility> all() {
		return REGISTRY.stream().toList();
	}

	public static void init() {
		// Lädt die Klasse, damit die Fähigkeiten registriert werden.
	}
}
