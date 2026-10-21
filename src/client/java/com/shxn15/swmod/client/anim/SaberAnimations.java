package com.shxn15.swmod.client.anim;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.combat.SaberAnimation;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.network.SaberAnimPayload;

/** Verwaltet die Animationszustände aller sichtbaren Lichtschwertträger. */
public final class SaberAnimations {
	private static final Int2ObjectMap<SaberAnimState> STATES = new Int2ObjectOpenHashMap<>();
	private static int clientTicks;

	private SaberAnimations() {
	}

	public static int ticks() {
		return clientTicks;
	}

	/** Aktuelle Animationszeit inkl. Teil-Tick. */
	public static float time(float partialTick) {
		return clientTicks + partialTick;
	}

	public static SaberAnimState get(Entity entity) {
		return STATES.computeIfAbsent(entity.getId(), id -> new SaberAnimState());
	}

	public static @Nullable SaberAnimState peek(Entity entity) {
		return STATES.get(entity.getId());
	}

	public static void tick(Minecraft minecraft) {
		clientTicks++;
		ClientLevel level = minecraft.level;
		if (level == null) {
			STATES.clear();
			return;
		}

		// Spieler und Mannequins (beides Avatare) können Lichtschwerter tragen.
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof Avatar avatar)) continue;
			ItemStack stack = avatar.getMainHandItem();
			SaberAnimState state = Lightsabers.isLightsaber(stack) ? get(avatar) : STATES.get(avatar.getId());
			if (state != null) {
				state.tick(avatar, stack, clientTicks);
			}
		}

		STATES.int2ObjectEntrySet().removeIf(entry -> {
			Entity entity = level.getEntity(entry.getIntKey());
			return !(entity instanceof LivingEntity living) || !living.isAlive() || !Lightsabers.isLightsaber(living.getMainHandItem());
		});
	}

	/** Server meldet eine Animation. */
	public static void handle(SaberAnimPayload payload) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}

		Entity entity = level.getEntity(payload.entityId());
		if (!(entity instanceof LivingEntity)) {
			return;
		}

		SaberAnimState state = get(entity);
		SaberAnimation animation = payload.anim();
		SwingDirection direction = SwingDirection.byId(payload.direction());

		if (animation == SaberAnimation.SWING) {
			state.startSwing(direction, clientTicks, payload.ticks());
		} else {
			state.startReaction(animation, direction, payload.ticks(), clientTicks);
		}
	}

	public static void clear() {
		STATES.clear();
	}
}
