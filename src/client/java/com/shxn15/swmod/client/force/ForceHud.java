package com.shxn15.swmod.client.force;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.gui.GuiShapes;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;

/**
 * Macht-HUD:
 * <ul>
 *     <li>Die Machtleiste direkt unter der XP-Leiste: eine Energieleiste, die beim Wirken sinkt und sich
 *     von selbst wieder auflädt. XP-Leiste, Herzen und Hunger rücken dafür ein Stück nach oben.</li>
 *     <li>Rechts neben der Hotbar ein Abzeichen mit der gewählten Fähigkeit und ihrer Abklingzeit.</li>
 *     <li>Kurz der Name einer frisch gewählten Fähigkeit unter dem Fadenkreuz.</li>
 * </ul>
 */
public final class ForceHud {
	/** So viele Pixel rücken XP-Leiste und alles darüber nach oben. */
	public static final int BAR_SHIFT = 6;
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;

	private static @Nullable ForceAbility toastAbility;
	private static int toastAt = -100;
	private static int selectionChangedAt = -100;
	private static int notReadyAt = -100;

	private ForceHud() {
	}

	public static void init() {
		// Vanilla-Leisten über der Hotbar nach oben schieben, wenn unsere Leiste sichtbar ist.
		for (Identifier id : List.of(VanillaHudElements.INFO_BAR, VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HEALTH_BAR,
				VanillaHudElements.ARMOR_BAR, VanillaHudElements.FOOD_BAR, VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH,
				VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.OVERLAY_MESSAGE)) {
			HudElementRegistry.replaceElement(id, original -> (graphics, deltaTracker) -> {
				if (!showsBar()) {
					original.extractRenderState(graphics, deltaTracker);
					return;
				}
				graphics.pose().pushMatrix();
				graphics.pose().translate(0f, -BAR_SHIFT);
				original.extractRenderState(graphics, deltaTracker);
				graphics.pose().popMatrix();
			});
		}

		// Das Fadenkreuz stünde sonst mitten im offenen Fähigkeitenrad.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original -> (graphics, deltaTracker) -> {
			if (!ForceInput.isWheelOpen()) {
				original.extractRenderState(graphics, deltaTracker);
			}
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.INFO_BAR, SwMod.id("force_bar"), ForceHud::extractBar);
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SwMod.id("force_slot"), ForceHud::extractSlot);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, SwMod.id("force_toast"), ForceHud::extractToast);
	}

	/** Die Leiste erscheint, sobald man mindestens eine Fähigkeit kennt. */
	public static boolean showsBar() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		return player != null && !player.isSpectator() && !minecraft.gui.hud.isHidden() && !ForceClientState.data().levels().isEmpty();
	}

	public static void onSelectionChanged(@Nullable ForceAbility ability) {
		toastAbility = ability;
		toastAt = ForceClientState.ticks();
		selectionChangedAt = ForceClientState.ticks();
	}

	public static void onNotReady() {
		notReadyAt = ForceClientState.ticks();
	}

	// ------------------------------------------------------------------------------------------
	// Machtleiste
	// ------------------------------------------------------------------------------------------

	private static void extractBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!showsBar()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float time = ForceClientState.ticks() + partialTick;
		float max = ForceClientState.maxForce();
		float shown = Mth.clamp(ForceClientState.shownForce(partialTick) / max, 0f, 1f);
		float ghost = Mth.clamp(ForceClientState.ghostForce(partialTick) / max, 0f, 1f);
		boolean infinite = minecraft.player.hasInfiniteMaterials();
		if (infinite || !ForceClientState.hasStatus()) {
			shown = 1f;
			ghost = 1f;
		}

		float left = (graphics.guiWidth() - BAR_WIDTH) / 2;
		float top = graphics.guiHeight() - 24 - BAR_HEIGHT;
		float right = left + BAR_WIDTH;
		float bottom = top + BAR_HEIGHT;
		float fill = left + BAR_WIDTH * shown;

		GuiShapes.Mesh mesh = GuiShapes.mesh();
		// Rahmen und leerer Hintergrund.
		mesh.rect(left - 1, top - 1, right + 1, bottom + 1, 0xFF04060B);
		mesh.rectV(left, top, right, bottom, 0xFF0D1626, 0xFF070C16);

		// Gerade verbrauchte Macht leuchtet kurz nach ("Geisterbalken").
		if (ghost > shown + 0.001f) {
			mesh.rect(fill, top, left + BAR_WIDTH * ghost, bottom, 0xD0B8E8FF);
		}

		// Füllung: tiefes Blau bis helles Cyan, oben ein Lichtstreifen.
		if (shown > 0f) {
			mesh.rectH(left, top, fill, bottom, 0xFF1640B8, ARGB.linearLerp(shown, 0xFF2A7CFF, 0xFF55D8FF));
			mesh.rectH(left, top, fill, top + 1, 0x80A0D8FF, 0xA0E0F8FF);
			mesh.rect(left, bottom - 1, fill, bottom, 0x50000018);
		}

		// Unterteilung in Energiezellen (alle 20 Punkte).
		for (float f = 20f; f < max - 0.5f; f += 20f) {
			float x = left + BAR_WIDTH * f / max;
			mesh.rect(x, top + 1, x + 1, bottom - 1, 0x60000000);
		}

		// Kosten der gewählten Fähigkeit: so viel würde der nächste Einsatz verbrauchen.
		ForceAbility selected = ForceClientState.selected();
		if (selected != null && !infinite) {
			int level = ForceClientState.data().level(selected);
			float cost = selected.cost(level) / max;
			if (cost <= shown) {
				float pulse = 0.5f + 0.5f * Mth.sin(time * 0.25f);
				mesh.rect(fill - BAR_WIDTH * cost, top, fill, top + 1, ARGB.color((int) (90 + 80 * pulse), 0xFFFFFF));
			} else {
				float x = left + BAR_WIDTH * Math.min(1f, cost);
				mesh.rect(x - 0.5f, top - 1, x + 0.5f, bottom + 1, 0xC0FF4848);
			}
		}

		float noForce = ForceClientState.noForceFlash(partialTick);
		if (noForce > 0f) {
			float blink = (Mth.sin(noForce * 16f * 1.6f) * 0.5f + 0.5f) * noForce;
			mesh.rect(left - 1, top - 1, right + 1, bottom + 1, ARGB.color((int) (170 * blink), 0xFF2020));
		}
		mesh.submit(graphics, false);

		// Leuchten: wandernder Glanz, helle Kante am Füllstand, Aufblitzen wenn voll.
		GuiShapes.Mesh glow = GuiShapes.mesh();
		if (shown > 0.02f) {
			float sweep = left - 30f + (time * 2.4f) % (BAR_WIDTH + 60f);
			float a = Math.max(left, sweep - 16f);
			float b = Math.min(fill, sweep + 16f);
			if (b > a) {
				float mid = Mth.clamp(sweep, a, b);
				glow.rectH(a, top, mid, bottom, 0x00FFFFFF, 0x50FFFFFF);
				glow.rectH(mid, top, b, bottom, 0x50FFFFFF, 0x00FFFFFF);
			}
			float flicker = 0.75f + 0.25f * Mth.sin(time * 0.9f);
			glow.glow(fill, top + BAR_HEIGHT * 0.5f, 6f, ARGB.color((int) (150 * flicker), 0x7FDFFF));
		}
		float full = ForceClientState.fullFlash(partialTick);
		if (full > 0f) {
			glow.rect(left, top, right, bottom, ARGB.color((int) (160 * full), 0x9FE6FF));
			glow.rect(left - 2, top - 2, right + 2, bottom + 2, ARGB.color((int) (50 * full), 0x5FB8FF));
		}
		glow.submit(graphics, true);
	}

	// ------------------------------------------------------------------------------------------
	// Gewählte Fähigkeit
	// ------------------------------------------------------------------------------------------

	private static void extractSlot(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!showsBar()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float time = ForceClientState.ticks() + partialTick;
		ForceAbility ability = ForceClientState.selected();

		// Rechts neben der Hotbar, aber nicht auf Angriffsanzeige oder Zweithand-Platz.
		float x = graphics.guiWidth() / 2f + 91 + 17;
		boolean rightOccupied = player.getMainArm() == HumanoidArm.LEFT && !player.getOffhandItem().isEmpty()
				|| minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR && player.getMainArm() == HumanoidArm.RIGHT;
		if (rightOccupied) {
			x += 24;
		}
		float y = graphics.guiHeight() - 11f;

		float sincePick = time - selectionChangedAt;
		float pop = sincePick < 8f ? 1f + 0.35f * Mth.sin(sincePick / 8f * Mth.PI) : 1f;
		float sinceNotReady = time - notReadyAt;
		float shake = sinceNotReady < 8f ? Mth.sin(sinceNotReady * 3.2f) * 2f * (1f - sinceNotReady / 8f) : 0f;
		x += shake;

		int color = ability != null ? ability.color() : 0x5A6270;
		boolean channeling = ability != null && ForceInput.channeling() == ability;

		GuiShapes.Mesh mesh = GuiShapes.mesh();
		mesh.disc(x, y, 10.5f, 0xE0101826, 0xE0060A12);
		mesh.ring(x, y, 10.5f, 12f, ARGB.color(230, color), ARGB.color(120, color));
		mesh.submit(graphics, false);

		if (ability != null) {
			GuiShapes.Mesh glow = GuiShapes.mesh();
			float pulse = channeling ? 0.6f + 0.4f * Mth.sin(time * 0.6f) : 0.25f + 0.1f * Mth.sin(time * 0.12f);
			glow.glow(x, y, 17f * pop, ARGB.color((int) (140 * pulse), color));
			glow.submit(graphics, true);

			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(pop, pop);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ability.icon(), -8, -8, 16, 16);
			graphics.pose().popMatrix();

			float cooldown = ForceClientState.cooldownFraction(ability, partialTick);
			if (cooldown > 0f) {
				GuiShapes.Mesh shade = GuiShapes.mesh();
				shade.arc(x, y, 0f, 10.5f, 0f, Mth.TWO_PI * cooldown, 0xB0000000, 0xB0000000);
				shade.submit(graphics, false);
				int seconds = Mth.ceil(ForceClientState.cooldownLeft(ability) / 20f);
				if (seconds > 0) {
					Font font = minecraft.font;
					String text = Integer.toString(seconds);
					graphics.text(font, text, (int) x - font.width(text) / 2, (int) y - 4, 0xFFFFFFFF, true);
				}
			} else if (sincePick >= 0 && ForceClientState.cooldownFraction(ability, partialTick) == 0f
					&& !ForceClientState.canAfford(ability, ForceClientState.data().level(ability))) {
				GuiShapes.Mesh shade = GuiShapes.mesh();
				shade.disc(x, y, 10.5f, 0x70300000, 0x90400000);
				shade.submit(graphics, false);
			}
		} else {
			Font font = minecraft.font;
			Component key = ForceInput.WHEEL.getTranslatedKeyMessage();
			graphics.text(font, key, (int) x - font.width(key) / 2, (int) y - 4, 0xFF8A93A3, true);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Name der frisch gewählten Fähigkeit
	// ------------------------------------------------------------------------------------------

	private static void extractToast(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || ForceInput.isWheelOpen()) {
			return;
		}
		float time = ForceClientState.ticks() + deltaTracker.getGameTimeDeltaPartialTick(false);
		float age = time - toastAt;
		if (age < 0 || age > 40f) {
			return;
		}

		float alpha = age < 4f ? age / 4f : age > 30f ? 1f - (age - 30f) / 10f : 1f;
		Font font = minecraft.font;
		Component text = toastAbility != null ? toastAbility.name() : Component.translatable("force.swmod.none");
		int color = toastAbility != null ? toastAbility.color() : 0xB0B8C8;
		int width = font.width(text);
		float cx = graphics.guiWidth() / 2f;
		float cy = graphics.guiHeight() / 2f + 26f;

		GuiShapes.Mesh glow = GuiShapes.mesh();
		glow.rectH(cx - width / 2f - 18, cy - 2, cx, cy + 9, ARGB.color(0, color), ARGB.color((int) (70 * alpha), color));
		glow.rectH(cx, cy - 2, cx + width / 2f + 18, cy + 9, ARGB.color((int) (70 * alpha), color), ARGB.color(0, color));
		glow.submit(graphics, true);
		graphics.text(font, text, (int) (cx - width / 2f), (int) cy, ARGB.color((int) (255 * alpha), ARGB.linearLerp(0.35f, color, 0xFFFFFF)), true);
	}

	static ForceData data() {
		return ForceClientState.data();
	}
}
