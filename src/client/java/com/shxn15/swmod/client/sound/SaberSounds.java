package com.shxn15.swmod.client.sound;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;

/** Startet Brumm-Sounds für alle gezündeten Lichtschwerter in Hörweite. */
public final class SaberSounds {
	private static final double HEARING_RANGE_SQR = 40 * 40;
	private static final Int2ObjectMap<SaberHumSound> HUMS = new Int2ObjectOpenHashMap<>();

	private SaberSounds() {
	}

	public static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer listener = minecraft.player;
		if (level == null || listener == null) {
			HUMS.clear();
			return;
		}

		HUMS.int2ObjectEntrySet().removeIf(entry -> !minecraft.getSoundManager().isActive(entry.getValue()));

		for (Player player : level.players()) {
			if (player.distanceToSqr(listener) > HEARING_RANGE_SQR || HUMS.containsKey(player.getId())) continue;
			SaberAnimState anim = SaberAnimations.peek(player);
			if (anim != null && Lightsabers.isIgnited(player.getMainHandItem()) && !anim.isThrown()) {
				start(minecraft, player);
			}
		}

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof ThrownLightsaberEntity && !HUMS.containsKey(entity.getId())
					&& entity.distanceToSqr(listener) < HEARING_RANGE_SQR) {
				start(minecraft, entity);
			}
		}
	}

	private static void start(Minecraft minecraft, Entity entity) {
		SaberHumSound sound = new SaberHumSound(entity);
		HUMS.put(entity.getId(), sound);
		minecraft.getSoundManager().play(sound);
	}

	public static void clear() {
		HUMS.clear();
	}
}
