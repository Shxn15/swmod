package com.shxn15.swmod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.lightsaber.LightsaberData;

public final class ModDataComponents {
	public static final DataComponentType<LightsaberData> LIGHTSABER = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			SwMod.id("lightsaber"),
			DataComponentType.<LightsaberData>builder()
					.persistent(LightsaberData.CODEC)
					.networkSynchronized(LightsaberData.STREAM_CODEC)
					.build());

	private ModDataComponents() {
	}

	public static void init() {
		// Lädt die Klasse, damit die statischen Felder registriert werden.
	}
}
