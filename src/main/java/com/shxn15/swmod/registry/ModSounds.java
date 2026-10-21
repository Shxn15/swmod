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
