package com.shxn15.swmod.client.force;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.shxn15.swmod.ModItems;
import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;
import com.shxn15.swmod.network.ForceCastPayload;
import com.shxn15.swmod.network.ForceControlPayload;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Steuerung der Machtfähigkeiten:
 * <ul>
 *     <li>C halten: Fähigkeitenrad, mit der Maus wählen, Loslassen bestätigt. Kurz tippen lässt es offen
 *     (dann Klick, C oder Zahl 1-8 zum Wählen, 9 = keine Fähigkeit).</li>
 *     <li>Rechtsklick: gewählte Fähigkeit wirken (halten bei Kanal-Fähigkeiten). Ohne gewählte Fähigkeit
 *     funktioniert Rechtsklick ganz normal (Blocken, Bauen, Essen, ...).</li>
 *     <li>Machtgriff: Mausrad ändert den Abstand, Linksklick schleudert.</li>
 * </ul>
 */
public final class ForceInput {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(SwMod.id("force"));
	public static final KeyMapping WHEEL = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.swmod.force_wheel", InputConstants.Type.KEYBOARD, InputConstants.KEY_C, CATEGORY));

	/** Wer C länger hält, wählt beim Loslassen. Kürzer = antippen, das Rad bleibt offen. */
	private static final int HOLD_TICKS = 5;
	/** So weit (GUI-Pixel) kann der Wahlzeiger von der Mitte weg. */
	public static final float SELECTOR_RADIUS = 60f;
	/** Innerhalb dieses Radius ist "keine Fähigkeit" gewählt. */
	public static final float DEAD_ZONE = 20f;

	private static boolean wheelOpen;
	private static int wheelOpenedAt;
	private static int wheelClosedAt = -100;
	private static boolean wheelToggle;
	private static float selectorX;
	private static float selectorY;
	private static int hovered = -1;
	private static int hoverChangedAt;

	/** Ein Klick hat das Rad geschlossen: bis zum Loslassen kein Angriff bzw. keine Benutzung. */
	private static boolean suppressAttack;
	private static boolean suppressUse;

	private static @Nullable ForceAbility channeling;
	private static boolean captureUse;
	private static boolean useWasDown;

	private ForceInput() {
	}

	public static void init() {
		// Lädt die Klasse, damit die Tastenbelegung registriert wird.
	}

	// ------------------------------------------------------------------------------------------
	// Tick (vor der Vanilla-Tastenverarbeitung und vor der Lichtschwertsteuerung)
	// ------------------------------------------------------------------------------------------

	public static void startTick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			reset();
			return;
		}

		if (suppressAttack && !minecraft.options.keyAttack.isDown()) suppressAttack = false;
		if (suppressUse && !minecraft.options.keyUse.isDown()) suppressUse = false;

		handleWheel(minecraft, player);
		handleUse(minecraft, player);
	}

	private static void handleWheel(Minecraft minecraft, LocalPlayer player) {
		boolean pressed = false;
		while (WHEEL.consumeClick()) {
			pressed = true;
		}
		int now = ForceClientState.ticks();
		ForceData data = ForceClientState.data();

		if (!wheelOpen) {
			if (!pressed || minecraft.gui.screen() != null || player.isSpectator() || !player.isAlive()) {
				return;
			}
			if (data.levels().isEmpty()) {
				minecraft.gui.hud.setOverlayMessage(Component.translatable("message.swmod.force.no_abilities").withStyle(ChatFormatting.AQUA), false);
				return;
			}
			openWheel(data, now);
			player.playSound(ModSounds.FORCE_WHEEL_OPEN, 0.5f, 1.0f);
			return;
		}

		if (minecraft.gui.screen() != null || !player.isAlive()) {
			closeWheel(false);
			return;
		}

		// Zahlentasten: 1-8 wählen direkt, 9 = keine Fähigkeit.
		for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
			if (minecraft.options.keyHotbarSlots[i].consumeClick()) {
				hovered = i < ForceData.WHEEL_SLOTS ? i : -1;
				closeWheel(true);
				return;
			}
		}

		boolean click = false;
		while (minecraft.options.keyAttack.consumeClick()) {
			click = true;
			suppressAttack = true;
		}
		while (minecraft.options.keyUse.consumeClick()) {
			click = true;
			suppressUse = true;
		}

		boolean held = WHEEL.isDown();
		if (!held && !wheelToggle && now - wheelOpenedAt < HOLD_TICKS) {
			wheelToggle = true;
		}
		if (click || (wheelToggle && pressed) || (!held && !wheelToggle)) {
			closeWheel(true);
		}
	}

	private static void openWheel(ForceData data, int now) {
		wheelOpen = true;
		wheelToggle = false;
		wheelOpenedAt = now;
		hovered = data.selectedAbility() != null ? data.selected() : -1;
		// Der Zeiger startet auf der aktuellen Wahl: kurz antippen ändert nichts.
		if (hovered >= 0) {
			float angle = slotAngle(hovered);
			selectorX = Mth.sin(angle) * (DEAD_ZONE + 14f);
			selectorY = -Mth.cos(angle) * (DEAD_ZONE + 14f);
		} else {
			selectorX = 0f;
			selectorY = 0f;
		}
		hoverChangedAt = now;
		if (channeling != null) {
			stopChannel();
		}
	}

	private static void closeWheel(boolean confirm) {
		wheelOpen = false;
		wheelClosedAt = ForceClientState.ticks();
		Minecraft minecraft = Minecraft.getInstance();
		if (!confirm || minecraft.player == null) {
			return;
		}

		ForceData data = ForceClientState.data();
		int slot = hovered >= 0 && data.abilityInSlot(hovered) != null ? hovered : -1;
		if (slot != data.selected() || data.selectedAbility() == null && slot >= 0) {
			ForceClientState.setPendingSelection(slot);
			ClientPlayNetworking.send(new ForceControlPayload(ForceControlPayload.SELECT, slot));
		}
		minecraft.player.playSound(ModSounds.FORCE_WHEEL_SELECT, 0.6f, slot >= 0 ? 1.0f : 0.8f);
		ForceHud.onSelectionChanged(slot >= 0 ? data.abilityInSlot(slot) : null);
	}

	private static void handleUse(Minecraft minecraft, LocalPlayer player) {
		boolean screenOpen = minecraft.gui.screen() != null;
		// Wer ein Holocron in der Hand hat, will es öffnen und nicht wirken.
		boolean holocron = player.getMainHandItem().is(ModItems.HOLOCRON) || player.getOffhandItem().is(ModItems.HOLOCRON);
		ForceAbility selected = wheelOpen || screenOpen || holocron ? null : ForceClientState.selected();
		boolean canCast = selected != null && player.isAlive() && !player.isSpectator();
		captureUse = canCast || channeling != null || wheelOpen || suppressUse;

		boolean down = !screenOpen && !suppressUse && minecraft.options.keyUse.isDown();
		int clicks = 0;
		if (captureUse) {
			while (minecraft.options.keyUse.consumeClick()) {
				clicks++;
			}
		}

		if (channeling != null) {
			if (!down || selected != channeling || !player.isAlive()) {
				stopChannel();
			} else if (channeling == ForceAbilities.GRAB) {
				boolean hurl = false;
				while (minecraft.options.keyAttack.consumeClick()) {
					hurl = true;
				}
				if (hurl) {
					ClientPlayNetworking.send(new ForceControlPayload(ForceControlPayload.HURL, 0f));
					channeling = null;
					suppressAttack = true;
				}
			}
		} else if (canCast && (clicks > 0 || down && !useWasDown)) {
			// Auch ganz kurze Klicks zwischen zwei Ticks zählen.
			tryCast(player, selected);
		}
		useWasDown = down;
	}

	private static void tryCast(LocalPlayer player, ForceAbility ability) {
		int level = ForceClientState.data().level(ability);
		if (level <= 0) {
			return;
		}
		if (ForceClientState.cooldownLeft(ability) > 0) {
			player.playSound(ModSounds.FORCE_WHEEL_TICK, 0.5f, 0.6f);
			ForceHud.onNotReady();
			return;
		}
		if (!ForceClientState.canAfford(ability, level)) {
			ForceClientState.noForce();
			return;
		}

		ClientPlayNetworking.send(new ForceCastPayload(ForceCastPayload.START, ability.id()));
		if (ability.activation() == ForceAbility.Activation.CHANNEL) {
			channeling = ability;
		}
	}

	private static void stopChannel() {
		if (channeling != null) {
			ClientPlayNetworking.send(new ForceCastPayload(ForceCastPayload.STOP, channeling.id()));
			channeling = null;
		}
	}

	/** Der Server hat den Kanal beendet (oder gar nicht erst begonnen). */
	static void serverStoppedChannel(Identifier ability) {
		if (channeling != null && channeling.id().equals(ability)) {
			channeling = null;
		}
	}

	// ------------------------------------------------------------------------------------------
	// Maus (aus MouseHandlerMixin) und Mausrad
	// ------------------------------------------------------------------------------------------

	/**
	 * Mausbewegung, bevor die Kamera gedreht wird.
	 *
	 * @return true, wenn die Bewegung verbraucht wurde (Rad offen: die Kamera bleibt stehen)
	 */
	public static boolean onMouseMove(double dx, double dy) {
		if (!wheelOpen) {
			return false;
		}

		Minecraft minecraft = Minecraft.getInstance();
		double sensitivity = minecraft.options.sensitivity().get() * 0.6 + 0.2;
		float factor = (float) (sensitivity * sensitivity * sensitivity * 8.0 * 0.6);
		selectorX += (float) dx * factor;
		selectorY += (float) dy * factor;
		float length = Mth.sqrt(selectorX * selectorX + selectorY * selectorY);
		if (length > SELECTOR_RADIUS) {
			selectorX *= SELECTOR_RADIUS / length;
			selectorY *= SELECTOR_RADIUS / length;
			length = SELECTOR_RADIUS;
		}

		int slot = -1;
		if (length >= DEAD_ZONE) {
			float angle = (float) Math.atan2(selectorX, -selectorY);
			slot = Math.floorMod(Math.round(angle / (Mth.TWO_PI / ForceData.WHEEL_SLOTS)), ForceData.WHEEL_SLOTS);
		}
		setHovered(slot);
		return true;
	}

	private static void setHovered(int slot) {
		if (slot != hovered) {
			hovered = slot;
			hoverChangedAt = ForceClientState.ticks();
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.player != null) {
				minecraft.player.playSound(ModSounds.FORCE_WHEEL_TICK, 0.35f, slot >= 0 ? 1.0f + slot * 0.04f : 0.8f);
			}
		}
	}

	/** Fabric-Event vor dem Wechsel des Hotbar-Platzes per Mausrad. */
	public static boolean allowHotbarScroll(Inventory inventory, int oldSlot, int newSlot, double scrollX, double scrollY) {
		double amount = scrollY != 0 ? scrollY : -scrollX;
		if (wheelOpen) {
			int next = hovered < 0 ? (amount < 0 ? 0 : ForceData.WHEEL_SLOTS - 1)
					: Math.floorMod(hovered + (amount < 0 ? 1 : -1), ForceData.WHEEL_SLOTS);
			setHovered(next);
			float angle = slotAngle(next);
			selectorX = Mth.sin(angle) * (DEAD_ZONE + 14f);
			selectorY = -Mth.cos(angle) * (DEAD_ZONE + 14f);
			return false;
		}
		if (channeling == ForceAbilities.GRAB) {
			ClientPlayNetworking.send(new ForceControlPayload(ForceControlPayload.DISTANCE, amount > 0 ? 1f : -1f));
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------
	// Abfragen
	// ------------------------------------------------------------------------------------------

	/** Winkel eines Radplatzes (0 = oben, im Uhrzeigersinn). */
	public static float slotAngle(int slot) {
		return slot * Mth.TWO_PI / ForceData.WHEEL_SLOTS;
	}

	/** Rechtsklick gehört gerade uns (Vanilla-Benutzung wird unterdrückt). */
	public static boolean capturesUse() {
		return captureUse || wheelOpen || suppressUse;
	}

	/** Linksklick gehört gerade uns (kein Angriff, kein Abbauen, kein Lichtschwertschlag). */
	public static boolean blocksAttack() {
		return wheelOpen || suppressAttack || channeling == ForceAbilities.GRAB;
	}

	public static boolean isWheelOpen() {
		return wheelOpen;
	}

	public static int wheelOpenedAt() {
		return wheelOpenedAt;
	}

	public static int wheelClosedAt() {
		return wheelClosedAt;
	}

	public static int hovered() {
		return hovered;
	}

	public static int hoverChangedAt() {
		return hoverChangedAt;
	}

	public static float selectorX() {
		return selectorX;
	}

	public static float selectorY() {
		return selectorY;
	}

	public static @Nullable ForceAbility channeling() {
		return channeling;
	}

	public static void reset() {
		wheelOpen = false;
		channeling = null;
		captureUse = false;
		suppressAttack = false;
		suppressUse = false;
		useWasDown = false;
	}
}
