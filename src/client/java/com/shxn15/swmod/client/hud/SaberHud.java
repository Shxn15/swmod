package com.shxn15.swmod.client.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import com.shxn15.swmod.client.input.SaberInput;
import com.shxn15.swmod.combat.SaberTuning;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.lightsaber.Lightsabers;

/**
 * Kampf-HUD rund ums Fadenkreuz:
 * <ul>
 *     <li>Blockpunkte als schmaler Balken (grau, solange die Deckung gesperrt ist)</li>
 *     <li>Beim Halten der linken Maustaste: Richtungsanzeige der aktuellen Mausgeste</li>
 *     <li>Kurzes Aufleuchten der zuletzt ausgeführten Schlagrichtung</li>
 * </ul>
 */
public final class SaberHud {
	private static final int RING_RADIUS = 13;

	private SaberHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden() || !Lightsabers.isLightsaber(minecraft.player.getMainHandItem())) {
			return;
		}

		int cx = minecraft.getWindow().getGuiScaledWidth() / 2;
		int cy = minecraft.getWindow().getGuiScaledHeight() / 2;

		if (SaberInput.isSaberReady(minecraft)) {
			renderBlockBar(graphics, cx, cy);
			renderGesture(graphics, cx, cy);
		}
	}

	private static void renderBlockBar(GuiGraphicsExtractor graphics, int cx, int cy) {
		float fraction = Mth.clamp(SaberInput.blockPoints() / SaberTuning.MAX_BLOCK_POINTS, 0f, 1f);
		boolean locked = SaberInput.guardTicks() > 0;
		int width = 41;
		int x0 = cx - width / 2;
		int y0 = cy + 12;

		graphics.fill(x0 - 1, y0 - 1, x0 + width + 1, y0 + 3, 0x90000000);
		int color;
		if (locked) {
			color = 0xFF6E6E6E;
		} else if (fraction > 0.5f) {
			color = ARGB.linearLerp((fraction - 0.5f) * 2f, 0xFFFFC83C, 0xFF4FB4FF);
		} else {
			color = ARGB.linearLerp(fraction * 2f, 0xFFFF3C3C, 0xFFFFC83C);
		}
		graphics.fill(x0, y0, x0 + Math.round(width * fraction), y0 + 2, color);
	}

	private static void renderGesture(GuiGraphicsExtractor graphics, int cx, int cy) {
		boolean held = SaberInput.isAttackHeld();
		int sinceSwing = SaberInput.ticksSinceLastDirection();

		if (held) {
			// Acht Punkte: die möglichen Schlagrichtungen.
			for (SwingDirection direction : SwingDirection.values()) {
				if (direction.isStab()) continue;
				int px = cx + Math.round(direction.screenX * RING_RADIUS);
				int py = cy - Math.round(direction.screenY * RING_RADIUS);
				graphics.fill(px - 1, py - 1, px + 1, py + 1, 0x80FFFFFF);
			}

			// Aktuelle Geste als Linie vom Zentrum aus.
			double gx = SaberInput.gestureX();
			double gy = SaberInput.gestureY();
			double magnitude = Math.sqrt(gx * gx + gy * gy);
			if (magnitude > 0.2) {
				float length = (float) Math.min(1.0, magnitude / 2.4) * RING_RADIUS;
				drawLine(graphics, cx, cy, (float) (gx / magnitude), (float) (gy / magnitude), length, 0xC0FFFFFF);
			}
		}

		if (sinceSwing >= 0 && sinceSwing < 8) {
			SwingDirection direction = SaberInput.lastDirection();
			int alpha = (int) (255 * (1f - sinceSwing / 8f));
			int color = (alpha << 24) | 0x7FD4FF;
			if (direction.isStab()) {
				graphics.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
			} else {
				// Pfeil von der Start- zur Endseite des Schlags.
				float dx = direction.screenX;
				float dy = direction.screenY;
				drawLine(graphics, cx - Math.round(dx * RING_RADIUS), cy + Math.round(dy * RING_RADIUS), dx, dy, RING_RADIUS * 2, color);
			}
		}
	}

	/** Zeichnet eine Linie aus kleinen Quadraten; (dx, dy) zeigt nach rechts/oben. */
	private static void drawLine(GuiGraphicsExtractor graphics, int x, int y, float dx, float dy, float length, int color) {
		int steps = Math.max(1, Math.round(length));
		for (int i = 0; i <= steps; i++) {
			int px = x + Math.round(dx * i);
			int py = y - Math.round(dy * i);
			graphics.fill(px, py, px + 1, py + 1, color);
		}
	}
}
