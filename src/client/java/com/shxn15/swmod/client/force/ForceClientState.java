package com.shxn15.swmod.client.force;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;
import com.shxn15.swmod.force.ForceSystem;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceStatusPayload;
import com.shxn15.swmod.registry.ModSounds;

/** Was der Client über die eigene Macht weiß: Vorrat (vom Server), Abklingzeiten, Rückmeldungen. */
public final class ForceClientState {
	private static float force = -1f;
	private static float maxForce = 100f;
	/** Weich nachgeführte Anzeige des Vorrats. */
	private static float shownForce;
	private static float prevShownForce;
	/** "Geisterbalken": zeigt kurz, wie viel gerade verbraucht wurde, und schrumpft dann nach. */
	private static float ghostForce;
	private static float prevGhostForce;
	private static int ghostHold;

	private static final Object2IntMap<Identifier> COOLDOWN_END = new Object2IntOpenHashMap<>();
	private static final Object2IntMap<Identifier> COOLDOWN_TOTAL = new Object2IntOpenHashMap<>();

	/** Rot aufblitzen, wenn die Macht nicht reicht. */
	private static int noForceFlash;
	/** Kurzes Aufleuchten, wenn der Vorrat wieder voll ist. */
	private static int fullFlash;
	/** Rad-Platz, der lokal schon gewählt ist, aber noch nicht vom Server bestätigt. */
	private static int pendingSelection = Integer.MIN_VALUE;
	private static int ticks;

	private ForceClientState() {
	}

	public static ForceData data() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return ForceData.EMPTY;
		}
		ForceData data = ForceSystem.data(player);
		if (pendingSelection != Integer.MIN_VALUE) {
			if (data.selected() == pendingSelection) {
				pendingSelection = Integer.MIN_VALUE;
			} else {
				data = data.withSelected(pendingSelection);
			}
		}
		return data;
	}

	public static ForceData data(Player player) {
		return player == Minecraft.getInstance().player ? data() : ForceSystem.data(player);
	}

	public static void setPendingSelection(int slot) {
		pendingSelection = slot;
	}

	public static void tick() {
		ticks++;
		if (noForceFlash > 0) noForceFlash--;
		if (fullFlash > 0) fullFlash--;

		prevShownForce = shownForce;
		prevGhostForce = ghostForce;
		float target = Math.max(0f, force);
		boolean wasFull = shownForce >= maxForce - 0.01f;
		shownForce = Mth.lerp(0.45f, shownForce, target);
		if (Math.abs(shownForce - target) < 0.05f) {
			shownForce = target;
		}
		if (!wasFull && shownForce >= maxForce - 0.01f && maxForce > 0) {
			fullFlash = 14;
		}

		if (ghostForce < shownForce) {
			ghostForce = shownForce;
		} else if (ghostHold > 0) {
			ghostHold--;
		} else {
			ghostForce = Math.max(shownForce, ghostForce - Math.max(0.6f, (ghostForce - shownForce) * 0.12f));
		}
	}

	public static int ticks() {
		return ticks;
	}

	public static void handleStatus(ForceStatusPayload payload) {
		if (force >= 0 && payload.force() < force - 0.5f) {
			// Verbrauch: der Geisterbalken bleibt kurz stehen.
			ghostHold = 8;
		}
		if (force < 0) {
			shownForce = payload.force();
			prevShownForce = shownForce;
			ghostForce = shownForce;
		}
		force = payload.force();
		maxForce = Math.max(1f, payload.maxForce());
	}

	public static void handleEvent(ForceEventPayload payload) {
		Minecraft minecraft = Minecraft.getInstance();
		ForceAbility ability = ForceAbilities.REGISTRY.getValue(payload.ability());

		switch (payload.event()) {
			case ForceEventPayload.COOLDOWN -> {
				COOLDOWN_END.put(payload.ability(), ticks + payload.value());
				COOLDOWN_TOTAL.put(payload.ability(), Math.max(1, payload.value()));
			}
			case ForceEventPayload.NO_FORCE -> {
				noForce();
				ForceInput.serverStoppedChannel(payload.ability());
			}
			case ForceEventPayload.NO_TARGET -> {
				fail(minecraft, Component.translatable("message.swmod.force.no_target"));
				ForceInput.serverStoppedChannel(payload.ability());
			}
			case ForceEventPayload.TOO_HEAVY -> {
				fail(minecraft, Component.translatable("message.swmod.force.too_heavy"));
				ForceInput.serverStoppedChannel(payload.ability());
			}
			case ForceEventPayload.NOTHING_TO_HEAL -> fail(minecraft, Component.translatable("message.swmod.force.nothing_to_heal"));
			case ForceEventPayload.CHANNEL_ENDED -> ForceInput.serverStoppedChannel(payload.ability());
			case ForceEventPayload.LEARNED -> {
				if (ability != null) {
					HolocronEffects.onLearned(ability, payload.value());
				}
			}
			default -> {
			}
		}
	}

	/** Nicht genug Macht: Leiste blinkt rot, leises Verpuffen. */
	public static void noForce() {
		noForceFlash = 16;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.playSound(ModSounds.FORCE_FAIL, 0.6f, 1.0f);
		}
	}

	private static void fail(Minecraft minecraft, Component message) {
		if (minecraft.player != null) {
			minecraft.player.playSound(ModSounds.FORCE_FAIL, 0.5f, 1.2f);
			minecraft.gui.hud.setOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY), false);
		}
	}

	// ------------------------------------------------------------------------------------------

	public static boolean hasStatus() {
		return force >= 0;
	}

	public static float force() {
		return Math.max(0f, force);
	}

	public static float maxForce() {
		return maxForce;
	}

	public static float shownForce(float partialTick) {
		return Mth.lerp(partialTick, prevShownForce, shownForce);
	}

	public static float ghostForce(float partialTick) {
		return Mth.lerp(partialTick, prevGhostForce, ghostForce);
	}

	public static float noForceFlash(float partialTick) {
		return Math.max(0f, noForceFlash - partialTick) / 16f;
	}

	public static float fullFlash(float partialTick) {
		return Math.max(0f, fullFlash - partialTick) / 14f;
	}

	/** Verbleibende Abklingzeit in Ticks. */
	public static int cooldownLeft(ForceAbility ability) {
		return Math.max(0, COOLDOWN_END.getInt(ability.id()) - ticks);
	}

	/** 1 = gerade begonnen, 0 = bereit. */
	public static float cooldownFraction(ForceAbility ability, float partialTick) {
		int total = COOLDOWN_TOTAL.getInt(ability.id());
		if (total <= 0) {
			return 0f;
		}
		return Mth.clamp((COOLDOWN_END.getInt(ability.id()) - ticks - partialTick) / total, 0f, 1f);
	}

	public static boolean canAfford(ForceAbility ability, int level) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && (player.hasInfiniteMaterials() || force < 0 || force >= ability.cost(level));
	}

	public static @Nullable ForceAbility selected() {
		return data().selectedAbility();
	}

	public static void clear() {
		force = -1f;
		maxForce = 100f;
		shownForce = prevShownForce = ghostForce = prevGhostForce = 0f;
		COOLDOWN_END.clear();
		COOLDOWN_TOTAL.clear();
		pendingSelection = Integer.MIN_VALUE;
	}
}
