package com.shxn15.swmod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import com.shxn15.swmod.SwMod;

public final class ModSounds {
	public static final SoundEvent LIGHTSABER_IGNITE = register("lightsaber.ignite");
	public static final SoundEvent LIGHTSABER_RETRACT = register("lightsaber.retract");
	public static final SoundEvent LIGHTSABER_HUM = register("lightsaber.hum");
	public static final SoundEvent LIGHTSABER_SWING = register("lightsaber.swing");
	public static final SoundEvent LIGHTSABER_HIT = register("lightsaber.hit");
	public static final SoundEvent LIGHTSABER_CLASH = register("lightsaber.clash");
	public static final SoundEvent LIGHTSABER_BLOCK = register("lightsaber.block");
	public static final SoundEvent LIGHTSABER_PARRY = register("lightsaber.parry");
	public static final SoundEvent LIGHTSABER_GUARD_BREAK = register("lightsaber.guard_break");
	public static final SoundEvent LIGHTSABER_DEFLECT = register("lightsaber.deflect");
	public static final SoundEvent LIGHTSABER_THROW = register("lightsaber.throw");
	public static final SoundEvent LIGHTSABER_SPIN = register("lightsaber.spin");
	public static final SoundEvent LIGHTSABER_CATCH = register("lightsaber.catch");

	public static final SoundEvent FORCE_PUSH = register("force.push");
	public static final SoundEvent FORCE_PULL = register("force.pull");
	public static final SoundEvent FORCE_GRAB = register("force.grab");
	public static final SoundEvent FORCE_GRAB_LOOP = register("force.grab_loop");
	public static final SoundEvent FORCE_THROW = register("force.throw");
	public static final SoundEvent FORCE_SPEED = register("force.speed");
	public static final SoundEvent FORCE_SPEED_END = register("force.speed_end");
	public static final SoundEvent FORCE_HEAL = register("force.heal");
	public static final SoundEvent FORCE_LIGHTNING = register("force.lightning");
	public static final SoundEvent FORCE_LIGHTNING_LOOP = register("force.lightning_loop");
	public static final SoundEvent FORCE_LIGHTNING_HIT = register("force.lightning_hit");
	public static final SoundEvent FORCE_GRIP = register("force.grip");
	public static final SoundEvent FORCE_GRIP_LOOP = register("force.grip_loop");
	public static final SoundEvent FORCE_IMPACT = register("force.impact");
	public static final SoundEvent FORCE_FAIL = register("force.fail");
	public static final SoundEvent FORCE_WHEEL_OPEN = register("force.wheel_open");
	public static final SoundEvent FORCE_WHEEL_TICK = register("force.wheel_tick");
	public static final SoundEvent FORCE_WHEEL_SELECT = register("force.wheel_select");
	public static final SoundEvent HOLOCRON_OPEN = register("holocron.open");
	public static final SoundEvent HOLOCRON_LEARN = register("holocron.learn");
	public static final SoundEvent HOLOCRON_UPGRADE = register("holocron.upgrade");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = SwMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
