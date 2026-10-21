package com.shxn15.swmod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.force.ForceParticleOptions;

public final class ModParticles {
	public static final ParticleType<ForceParticleOptions> FORCE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, SwMod.id("force"),
			FabricParticleTypes.complex(false, ForceParticleOptions.CODEC, ForceParticleOptions.STREAM_CODEC));

	private ModParticles() {
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
