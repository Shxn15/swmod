package com.shxn15.swmod.client.force;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.gui.GuiShapes;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;
import com.shxn15.swmod.force.ForceStat;

/**
 * Das Fähigkeitenrad: acht Segmente rund um die Bildschirmmitte. Die Maus bewegt einen
 * leuchtenden Zeiger, das Segment darunter wölbt sich hervor und glüht in der Farbe seiner
 * Fähigkeit. In der Mitte liegt "keine Fähigkeit".
 */
public final class ForceWheelHud {
	private static final float INNER = 24f;
	private static final float OUTER = 74f;
	private static final float ICON_RADIUS = 50f;
	private static final float GAP = 0.04f;

	private ForceWheelHud() {
	}

	public static void init() {
		HudElementRegistry.addLast(SwMod.id("force_wheel"), ForceWheelHud::extract);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float time = ForceClientState.ticks() + partialTick;
		boolean open = ForceInput.isWheelOpen();
		float openness = open
				? Mth.clamp((time - ForceInput.wheelOpenedAt()) / 4f, 0f, 1f)
				: 1f - Mth.clamp((time - ForceInput.wheelClosedAt()) / 3f, 0f, 1f);
		if (openness <= 0f) {
			return;
		}

		ForceData data = ForceClientState.data();
		int hovered = open ? ForceInput.hovered() : -2;
		float alpha = easeOut(openness);
		float scale = 0.8f + 0.2f * easeOutBack(openness);
		float cx = graphics.guiWidth() / 2f;
		float cy = graphics.guiHeight() / 2f - 8f;
		float hoverAnim = Mth.clamp((time - ForceInput.hoverChangedAt()) / 3f, 0f, 1f);

		// Bildschirm abdunkeln, in der Mitte ein schwaches blaues Schimmern.
		GuiShapes.Mesh background = GuiShapes.mesh();
		background.rect(0, 0, graphics.guiWidth(), graphics.guiHeight(), ARGB.color((int) (110 * alpha), 0x02040A));
		background.submit(graphics, false);
		GuiShapes.Mesh aura = GuiShapes.mesh();
		aura.glow(cx, cy, OUTER * 2.2f * scale, ARGB.color((int) (60 * alpha), 0x2050A0));
		aura.submit(graphics, true);

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(scale, scale);

		GuiShapes.Mesh wheel = GuiShapes.mesh();
		GuiShapes.Mesh glow = GuiShapes.mesh();
		float half = Mth.PI / ForceData.WHEEL_SLOTS - GAP;

		for (int slot = 0; slot < ForceData.WHEEL_SLOTS; slot++) {
			ForceAbility ability = data.abilityInSlot(slot);
			float angle = ForceInput.slotAngle(slot);
			boolean isHovered = slot == hovered;
			float bulge = isHovered ? 6f * easeOutBack(hoverAnim) : 0f;
			float r0 = INNER + 3f + bulge * 0.3f;
			float r1 = OUTER + bulge;
			int color = ability != null ? ability.color() : 0x404858;

			int inner = ARGB.color((int) ((ability != null ? 215 : 150) * alpha), isHovered ? ARGB.linearLerp(0.25f, 0x0C1220, color) : 0x0C1220);
			int outer = ARGB.color((int) ((ability != null ? 230 : 150) * alpha), isHovered ? ARGB.linearLerp(0.45f, 0x101828, color) : 0x141C2C);
			wheel.arc(0, 0, r0, r1, angle - half, angle + half, inner, outer);
			wheel.arc(0, 0, r1 - 1.5f, r1, angle - half, angle + half, ARGB.color((int) (220 * alpha), color), ARGB.color((int) (220 * alpha), color));
			if (slot == data.selected() && ability != null) {
				// Markierung der aktuell gewählten Fähigkeit.
				wheel.arc(0, 0, r1 + 2f, r1 + 3.5f, angle - half * 0.5f, angle + half * 0.5f, ARGB.color((int) (255 * alpha), 0xFFFFFF),
						ARGB.color((int) (255 * alpha), 0xFFFFFF));
			}

			if (isHovered && ability != null) {
				float pulse = 0.75f + 0.25f * Mth.sin(time * 0.5f);
				glow.arc(0, 0, r0, r1, angle - half, angle + half, ARGB.color((int) (50 * alpha * pulse), color), ARGB.color((int) (120 * alpha * pulse), color));
				glow.arc(0, 0, r1, r1 + 14f, angle - half, angle + half, ARGB.color((int) (110 * alpha * pulse), color), ARGB.color(0, color));
			}

			// Abklingzeit als dunkler Schatten über dem Segment.
			if (ability != null) {
				float cooldown = ForceClientState.cooldownFraction(ability, partialTick);
				if (cooldown > 0f) {
					wheel.arc(0, 0, r0, r1, angle - half, angle - half + 2 * half * cooldown, ARGB.color((int) (150 * alpha), 0), ARGB.color((int) (150 * alpha), 0));
				}
			}
		}

		// Mitte: "keine Fähigkeit".
		boolean noneHovered = hovered == -1;
		wheel.disc(0, 0, INNER, ARGB.color((int) (230 * alpha), noneHovered ? 0x1A2438 : 0x0A0E18), ARGB.color((int) (230 * alpha), 0x121A2A));
		wheel.ring(0, 0, INNER, INNER + 1.5f, ARGB.color((int) (200 * alpha), noneHovered ? 0xE0E8FF : 0x506078),
				ARGB.color((int) (200 * alpha), noneHovered ? 0xE0E8FF : 0x506078));
		if (noneHovered) {
			glow.glow(0, 0, INNER + 10f, ARGB.color((int) (90 * alpha), 0x8FB4FF));
		}

		// Zeiger: leuchtender Punkt mit Spur zur Mitte.
		if (open) {
			float sx = ForceInput.selectorX();
			float sy = ForceInput.selectorY();
			glow.softLine(0, 0, sx, sy, 3f, ARGB.color(0, 0xFFFFFF), ARGB.color((int) (120 * alpha), 0xCFE6FF));
			glow.glow(sx, sy, 7f, ARGB.color((int) (230 * alpha), 0xDDEEFF));
		}
		wheel.submit(graphics, false);
		glow.submit(graphics, true);

		// Symbole und Stufen.
		Font font = minecraft.font;
		for (int slot = 0; slot < ForceData.WHEEL_SLOTS; slot++) {
			ForceAbility ability = data.abilityInSlot(slot);
			float angle = ForceInput.slotAngle(slot);
			boolean isHovered = slot == hovered;
			float bulge = isHovered ? 6f * easeOutBack(hoverAnim) : 0f;
			float radius = ICON_RADIUS + bulge * 0.7f;
			float ix = Mth.sin(angle) * radius;
			float iy = -Mth.cos(angle) * radius;

			graphics.pose().pushMatrix();
			graphics.pose().translate(ix, iy);
			if (ability != null) {
				float size = isHovered ? 1.0f + 0.25f * easeOutBack(hoverAnim) : 1.0f;
				graphics.pose().scale(size, size);
				boolean ready = ForceClientState.cooldownLeft(ability) == 0;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ability.icon(), -10, -12, 20, 20, ARGB.white(alpha * (ready ? 1f : 0.55f)));
				extractPips(graphics, data.level(ability), ability.maxLevel(), ability.color(), alpha);
			} else {
				graphics.text(font, "+", -font.width("+") / 2, -4, ARGB.color((int) (110 * alpha), 0x8090A8), false);
			}
			graphics.pose().popMatrix();
		}

		Component center = Component.translatable("force.swmod.none");
		graphics.pose().pushMatrix();
		graphics.pose().scale(0.75f, 0.75f);
		graphics.centeredText(font, center, 0, -4, ARGB.color((int) (255 * alpha), noneHovered ? 0xFFFFFF : 0x8A94A8));
		graphics.pose().popMatrix();
		graphics.pose().popMatrix();

		Component hint = Component.translatable("force.swmod.wheel.hint", ForceInput.WHEEL.getTranslatedKeyMessage());
		int hintY = (int) Math.max(4f, cy - (OUTER + 8f) * scale - 12f);
		graphics.centeredText(font, hint, (int) cx, hintY, ARGB.color((int) (180 * alpha), 0x8890A0));

		extractInfo(graphics, font, data, hovered, cx, cy, (OUTER + 10f) * scale, alpha);
	}

	/** Kleine Rauten unter dem Symbol: eine je Stufe. */
	private static void extractPips(GuiGraphicsExtractor graphics, int level, int maxLevel, int color, float alpha) {
		GuiShapes.Mesh pips = GuiShapes.mesh();
		float width = maxLevel * 5f - 2f;
		for (int i = 0; i < maxLevel; i++) {
			float x = -width / 2f + i * 5f + 1.5f;
			float y = 11f;
			int c = i < level ? ARGB.color((int) (255 * alpha), ARGB.linearLerp(0.4f, color, 0xFFFFFF)) : ARGB.color((int) (160 * alpha), 0x303848);
			pips.quad(x, y - 1.6f, c, x - 1.6f, y, c, x, y + 1.6f, c, x + 1.6f, y, c);
		}
		pips.submit(graphics, false);
	}

	/**
	 * Name, Beschreibung und Kosten der Fähigkeit unter dem Zeiger in einer kleinen Tafel rechts
	 * neben dem Rad. Ist rechts zu wenig Platz, steht die Tafel unter dem Rad.
	 */
	private static void extractInfo(GuiGraphicsExtractor graphics, Font font, ForceData data, int hovered, float cx, float cy, float wheelRadius,
									float alpha) {
		ForceAbility ability = hovered >= 0 ? data.abilityInSlot(hovered) : null;
		int a = (int) (255 * alpha);
		float sideLeft = cx + wheelRadius + 12f;
		int sideWidth = (int) Math.min(150f, graphics.guiWidth() - sideLeft - 8f);
		boolean side = sideWidth >= 100;
		int width = side ? sideWidth : Math.min(240, graphics.guiWidth() - 16);

		Component title;
		int accent;
		List<FormattedCharSequence> lines;
		List<FormattedCharSequence> footer = List.of();
		if (ability == null) {
			title = Component.translatable(hovered == -1 ? "force.swmod.none" : "force.swmod.wheel.empty_title");
			accent = 0x8FA4C8;
			lines = font.split(Component.translatable(hovered == -1 ? "force.swmod.none.desc" : "force.swmod.wheel.empty"), width - 10);
		} else {
			int level = data.level(ability);
			title = Component.empty().append(ability.name())
					.append(Component.literal(" " + "◆".repeat(level) + "◇".repeat(ability.maxLevel() - level)).withStyle(ChatFormatting.GRAY));
			accent = ability.color();
			lines = font.split(ability.description(), width - 10);
			Component cost = ability.activation() == ForceAbility.Activation.CHANNEL
					? Component.translatable("force.swmod.wheel.cost_channel", ForceStat.format(ability.cost(level)), ForceStat.format(ability.drain(level) * 20))
					: Component.translatable("force.swmod.wheel.cost", ForceStat.format(ability.cost(level)));
			footer = font.split(cost, width - 10);
		}

		int height = 6 + 10 + 3 + lines.size() * 10 + (footer.isEmpty() ? 0 : 2 + footer.size() * 10) + 3;
		int textWidth = Math.max(lines.stream().mapToInt(font::width).max().orElse(0), footer.stream().mapToInt(font::width).max().orElse(0));
		int boxWidth = side ? width : Math.min(width, Math.max(font.width(title), textWidth) + 16);
		int x0 = side ? (int) sideLeft : (int) (cx - boxWidth / 2f);
		int y0 = side ? (int) (cy - height / 2f) : (int) (cy + wheelRadius + 6f);

		GuiShapes.Mesh box = GuiShapes.mesh();
		box.rect(x0, y0, x0 + boxWidth, y0 + height, ARGB.color((int) (205 * alpha), 0x080C16));
		box.rectH(x0, y0, x0 + boxWidth, y0 + 1, ARGB.color((int) (200 * alpha), accent), ARGB.color(0, accent));
		box.rect(x0, y0, x0 + 2, y0 + height, ARGB.color((int) (230 * alpha), accent));
		box.submit(graphics, false);
		GuiShapes.Mesh glow = GuiShapes.mesh();
		glow.rectH(x0 + 2, y0, x0 + 26, y0 + height, ARGB.color((int) (40 * alpha), accent), ARGB.color(0, accent));
		glow.submit(graphics, true);

		int textX = x0 + 8;
		int y = y0 + 6;
		graphics.text(font, title, textX, y, ARGB.color(a, ARGB.linearLerp(0.35f, accent, 0xFFFFFF)), true);
		y += 13;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, textX, y, ARGB.color(a, 0xC0C8D8), false);
			y += 10;
		}
		y += 2;
		for (FormattedCharSequence line : footer) {
			graphics.text(font, line, textX, y, ARGB.color(a, 0x6FC8FF), false);
			y += 10;
		}
	}

	static float easeOut(float t) {
		float inv = 1f - Mth.clamp(t, 0f, 1f);
		return 1f - inv * inv * inv;
	}

	static float easeOutBack(float t) {
		t = Mth.clamp(t, 0f, 1f);
		float c1 = 1.70158f;
		float c3 = c1 + 1f;
		return 1f + c3 * (float) Math.pow(t - 1f, 3) + c1 * (float) Math.pow(t - 1f, 2);
	}
}
