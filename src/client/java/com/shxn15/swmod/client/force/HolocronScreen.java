package com.shxn15.swmod.client.force;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.shxn15.swmod.client.gui.GuiShapes;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.network.HolocronPayload;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Das Wissen des Holocrons: in der Mitte schwebt der Holocron-Würfel, um ihn herum die Fähigkeiten
 * als Sternbild. Anklicken zeigt Details und Werte, "Lernen" kostet Erfahrungslevel und wird mit
 * einem Lichtfeuerwerk gefeiert. Links das Fähigkeitenrad: Symbole einfach hineinziehen.
 */
public class HolocronScreen extends Screen {
	private static final int NODE_RADIUS = 13;
	private static final int SLOT_RADIUS = 9;

	private final List<ForceAbility> abilities = ForceAbilities.all();
	private final RandomSource random = RandomSource.create();
	private final List<Spark> sparks = new ArrayList<>();
	private final List<float[]> stars = new ArrayList<>();
	private int age;

	private @Nullable ForceAbility focused;
	private @Nullable ForceAbility lastHovered;

	// Ziehen & Ablegen aufs Rad
	private @Nullable ForceAbility pressed;
	private @Nullable ForceAbility dragging;
	private double pressX;
	private double pressY;

	// Feier-Effekte
	private @Nullable ForceAbility charging;
	private int chargeStart = -100;
	private @Nullable ForceAbility celebrated;
	private int celebrateStart = -100;
	private int celebrateLevel;
	private float flash;

	// Layout
	private float holoX;
	private float holoY;
	private float orbit;
	private float wheelX;
	private float wheelY;
	private float wheelRadius;
	private int panelX;
	private int panelY;
	private int panelW;
	private int panelH;
	private int buttonX;
	private int buttonY;
	private int buttonW;
	private int buttonH;

	public HolocronScreen() {
		super(Component.translatable("screen.swmod.holocron"));
	}

	@Override
	protected void init() {
		boolean narrow = this.width < 420;
		this.panelW = Mth.clamp((int) (this.width * 0.34f), 140, 190);
		this.panelX = this.width - this.panelW - 8;
		this.panelY = 26;
		this.panelH = this.height - this.panelY - 10;

		float left = narrow ? 64f : this.width * 0.15f;
		this.wheelRadius = narrow ? 24f : 30f;
		this.wheelX = Math.max(this.wheelRadius + 14f, left);
		this.wheelY = this.height * 0.56f;

		float centerLeft = this.wheelX + this.wheelRadius + 20f;
		float centerRight = this.panelX - 10f;
		this.holoX = (centerLeft + centerRight) / 2f;
		this.holoY = this.height * 0.55f;
		this.orbit = Math.min((centerRight - centerLeft) / 2f - NODE_RADIUS - 4f, this.height * 0.33f);

		this.buttonW = this.panelW - 20;
		this.buttonH = 18;
		this.buttonX = this.panelX + 10;
		this.buttonY = this.panelY + this.panelH - this.buttonH - 8;

		this.stars.clear();
		RandomSource starRandom = RandomSource.create(1977);
		for (int i = 0; i < 90; i++) {
			this.stars.add(new float[]{starRandom.nextFloat() * this.width, starRandom.nextFloat() * this.height,
					0.4f + starRandom.nextFloat() * 1.1f, starRandom.nextFloat() * Mth.TWO_PI});
		}
		if (this.focused == null) {
			this.focused = this.abilities.isEmpty() ? null : this.abilities.getFirst();
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void tick() {
		this.age++;
		this.flash = Math.max(0f, this.flash - 0.06f);
		this.sparks.removeIf(spark -> {
			spark.x += spark.vx;
			spark.y += spark.vy;
			spark.vx *= spark.drag;
			spark.vy = spark.vy * spark.drag + spark.gravity;
			spark.prevLife = spark.life;
			spark.life -= 1f / spark.maxAge;
			return spark.life <= 0f;
		});
		if (this.minecraft.player == null || !this.minecraft.player.isAlive()) {
			this.onClose();
		}
	}

	// ------------------------------------------------------------------------------------------
	// Zeichnen
	// ------------------------------------------------------------------------------------------

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		float time = this.age + a;
		float intro = Mth.clamp(time / 10f, 0f, 1f);
		GuiShapes.Mesh mesh = GuiShapes.mesh();
		mesh.rectV(0, 0, this.width, this.height, ARGB.color((int) (215 * intro), 0x03060E), ARGB.color((int) (235 * intro), 0x060A18));
		mesh.submit(graphics, false);

		GuiShapes.Mesh glow = GuiShapes.mesh();
		// Nebel: blau (helle Seite) links, rot (dunkle Seite) rechts vom Holocron.
		glow.glow(this.holoX - this.orbit * 0.8f, this.holoY - 10, this.orbit * 2.2f, ARGB.color((int) (45 * intro), ForceSide.LIGHT.color));
		glow.glow(this.holoX + this.orbit * 0.9f, this.holoY + 10, this.orbit * 1.8f, ARGB.color((int) (35 * intro), ForceSide.DARK.color));
		for (float[] star : this.stars) {
			float twinkle = 0.45f + 0.55f * Mth.sin(time * 0.06f * star[2] + star[3]);
			glow.glow(star[0], star[1], 1.6f * star[2], ARGB.color((int) (200 * twinkle * intro), 0xD8E6FF));
		}
		glow.submit(graphics, true);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		float time = this.age + a;
		ForceData data = ForceClientState.data();
		ForceAbility hovered = this.nodeAt(mouseX, mouseY);
		if (hovered != this.lastHovered && hovered != null) {
			this.minecraft.player.playSound(ModSounds.FORCE_WHEEL_TICK, 0.3f, 1.2f);
		}
		this.lastHovered = hovered;

		this.extractTitle(graphics, time);
		this.extractBeams(graphics, data, time);
		this.extractHolocron(graphics, time);
		this.extractNodes(graphics, data, hovered, time);
		this.extractWheel(graphics, data, mouseX, mouseY, time);
		this.extractPanel(graphics, data, mouseX, mouseY, time);
		this.extractSparks(graphics, a);
		this.extractCelebration(graphics, time);

		if (this.dragging != null) {
			GuiShapes.Mesh glow = GuiShapes.mesh();
			glow.glow(mouseX, mouseY, 18f, ARGB.color(150, this.dragging.color()));
			glow.submit(graphics, true);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.dragging.icon(), mouseX - 10, mouseY - 10, 20, 20);
		} else if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(this.font, this.tooltip(hovered, data), mouseX, mouseY);
		}
	}

	private void extractTitle(GuiGraphicsExtractor graphics, float time) {
		Component title = Component.translatable("screen.swmod.holocron").withStyle(ChatFormatting.BOLD);
		float glow = 0.6f + 0.4f * Mth.sin(time * 0.08f);
		GuiShapes.Mesh mesh = GuiShapes.mesh();
		float w = this.font.width(title);
		float cx = this.holoX;
		mesh.rectH(cx - w / 2f - 30, 9, cx, 20, 0x00000000, ARGB.color((int) (60 * glow), 0x4FA8FF));
		mesh.rectH(cx, 9, cx + w / 2f + 30, 20, ARGB.color((int) (60 * glow), 0x4FA8FF), 0x00000000);
		mesh.submit(graphics, true);
		graphics.centeredText(this.font, title, (int) cx, 11, 0xFFDDEEFF);

		LocalPlayer player = this.minecraft.player;
		Component xp = player.hasInfiniteMaterials()
				? Component.translatable("screen.swmod.holocron.creative")
				: Component.translatable("screen.swmod.holocron.xp", player.experienceLevel);
		graphics.text(this.font, xp, 8, 8, 0xFF80FF20, true);
	}

	/** Lichtbahnen vom Holocron zu jeder Fähigkeit; erlernte pulsieren mit fließender Energie. */
	private void extractBeams(GuiGraphicsExtractor graphics, ForceData data, float time) {
		GuiShapes.Mesh glow = GuiShapes.mesh();
		GuiShapes.Mesh dim = GuiShapes.mesh();
		for (int i = 0; i < this.abilities.size(); i++) {
			ForceAbility ability = this.abilities.get(i);
			float[] pos = this.nodePos(i, time);
			float dx = pos[0] - this.holoX;
			float dy = pos[1] - this.holoY;
			float length = Mth.sqrt(dx * dx + dy * dy);
			float sx = this.holoX + dx / length * 20f;
			float sy = this.holoY + dy / length * 20f;
			float ex = pos[0] - dx / length * (NODE_RADIUS + 2f);
			float ey = pos[1] - dy / length * (NODE_RADIUS + 2f);
			int color = ability.color();

			if (data.knows(ability)) {
				glow.softLine(sx, sy, ex, ey, 5f, ARGB.color(70, color), ARGB.color(130, color));
				glow.line(sx, sy, ex, ey, 1f, ARGB.color(150, color), ARGB.color(220, color));
				// Energiepunkte, die vom Holocron nach außen fließen.
				for (int k = 0; k < 3; k++) {
					float t = ((time * 0.025f + k / 3f + i * 0.13f) % 1f);
					float px = Mth.lerp(t, sx, ex);
					float py = Mth.lerp(t, sy, ey);
					glow.glow(px, py, 3.5f, ARGB.color((int) (200 * Mth.sin(t * Mth.PI)), ARGB.linearLerp(0.5f, color, 0xFFFFFF)));
				}
			} else {
				// Gestrichelt und gedämpft: dieses Wissen schläft noch.
				int dashes = Math.max(3, (int) (length / 8f));
				for (int k = 0; k < dashes; k += 2) {
					float t0 = k / (float) dashes;
					float t1 = (k + 1) / (float) dashes;
					dim.line(Mth.lerp(t0, sx, ex), Mth.lerp(t0, sy, ey), Mth.lerp(t1, sx, ex), Mth.lerp(t1, sy, ey), 1f, 0x60607088, 0x60607088);
				}
			}

			// Lade-Impuls beim Klick auf "Lernen": ein Lichtball rast zur Fähigkeit.
			if (ability == this.charging) {
				float t = (time - this.chargeStart) / 7f;
				if (t >= 0f && t <= 1f) {
					float px = Mth.lerp(t, sx, ex);
					float py = Mth.lerp(t, sy, ey);
					glow.glow(px, py, 10f, ARGB.color(255, ARGB.linearLerp(0.6f, color, 0xFFFFFF)));
					glow.softLine(sx, sy, px, py, 6f, ARGB.color(0, color), ARGB.color(200, color));
				}
			}
		}
		dim.submit(graphics, false);
		glow.submit(graphics, true);
	}

	/** Der Holocron: ein schwebender, sich drehender Kristallwürfel mit pulsierendem Kern. */
	private void extractHolocron(GuiGraphicsExtractor graphics, float time) {
		float intro = ForceWheelHud.easeOutBack(Mth.clamp(time / 12f, 0f, 1f));
		float size = 13f * intro;
		float bob = Mth.sin(time * 0.07f) * 2f;
		float cx = this.holoX;
		float cy = this.holoY + bob;
		float yaw = time * 0.035f;
		float pitch = 0.55f + Mth.sin(time * 0.021f) * 0.15f;
		float pulse = 0.7f + 0.3f * Mth.sin(time * 0.15f);

		Vector3f[] corners = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			Vector3f v = new Vector3f((i & 1) == 0 ? -1 : 1, (i & 2) == 0 ? -1 : 1, (i & 4) == 0 ? -1 : 1).mul(size);
			v.rotateY(yaw).rotateX(pitch);
			corners[i] = v;
		}

		GuiShapes.Mesh back = GuiShapes.mesh();
		back.glow(cx, cy, 46f * intro, ARGB.color((int) (110 * pulse), 0x3F8FFF));
		back.submit(graphics, true);

		// Flächen von hinten nach vorne, halbdurchsichtig.
		int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
		Integer[] order = {0, 1, 2, 3, 4, 5};
		Arrays.sort(order, Comparator.comparingDouble(f -> {
			float z = 0;
			for (int index : faces[f]) z += corners[index].z;
			return -z;
		}));
		GuiShapes.Mesh body = GuiShapes.mesh();
		for (int f : order) {
			int[] face = faces[f];
			float depth = 0;
			for (int index : face) depth += corners[index].z;
			int shade = depth > 0 ? 0x183A70 : 0x2A64B8;
			int c = ARGB.color(depth > 0 ? 120 : 90, shade);
			body.quad(cx + corners[face[0]].x, cy + corners[face[0]].y, c, cx + corners[face[1]].x, cy + corners[face[1]].y, c,
					cx + corners[face[2]].x, cy + corners[face[2]].y, c, cx + corners[face[3]].x, cy + corners[face[3]].y, c);
		}
		body.submit(graphics, false);

		GuiShapes.Mesh edges = GuiShapes.mesh();
		int[][] lines = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] line : lines) {
			Vector3f p = corners[line[0]];
			Vector3f q = corners[line[1]];
			edges.softLine(cx + p.x, cy + p.y, cx + q.x, cy + q.y, 4f, ARGB.color(120, 0x6FC0FF), ARGB.color(120, 0x6FC0FF));
			edges.line(cx + p.x, cy + p.y, cx + q.x, cy + q.y, 1f, 0xD0D8F0FF, 0xD0D8F0FF);
		}
		for (Vector3f corner : corners) {
			edges.glow(cx + corner.x, cy + corner.y, 3.5f, 0xC0FFFFFF);
		}
		edges.glow(cx, cy, 10f * pulse, ARGB.color(255, 0xBFE6FF));
		edges.glow(cx, cy, 4f, 0xFFFFFFFF);
		edges.submit(graphics, true);
	}

	private void extractNodes(GuiGraphicsExtractor graphics, ForceData data, @Nullable ForceAbility hovered, float time) {
		LocalPlayer player = this.minecraft.player;
		for (int i = 0; i < this.abilities.size(); i++) {
			ForceAbility ability = this.abilities.get(i);
			float[] pos = this.nodePos(i, time);
			float x = pos[0];
			float y = pos[1];
			int level = data.level(ability);
			boolean known = level > 0;
			boolean maxed = level >= ability.maxLevel();
			boolean affordable = !maxed && (player.hasInfiniteMaterials() || player.experienceLevel >= ability.learnCost(level + 1));
			boolean highlight = ability == hovered || ability == this.focused;
			int color = ability.color();

			float pop = 1f;
			if (ability == this.celebrated) {
				float t = (time - this.celebrateStart) / 14f;
				if (t >= 0f && t <= 1f) pop = 1f + 0.6f * Mth.sin(t * Mth.PI) * (1f - t * 0.5f);
			}
			float radius = NODE_RADIUS * pop * (highlight ? 1.12f : 1f);

			GuiShapes.Mesh glow = GuiShapes.mesh();
			if (known || highlight || affordable) {
				float pulse = affordable ? 0.55f + 0.45f * Mth.sin(time * 0.2f + i) : 0.6f;
				glow.glow(x, y, radius * 2.1f, ARGB.color((int) ((known ? 110 : 60) * pulse + (highlight ? 60 : 0)), color));
			}
			glow.submit(graphics, true);

			GuiShapes.Mesh node = GuiShapes.mesh();
			node.disc(x, y, radius, known ? 0xF0101A2C : 0xF0080A10, known ? 0xF0182844 : 0xF00C0E16);
			int ring = maxed ? 0xFFFFD45A : known ? ARGB.opaque(color) : 0xFF3A4252;
			node.ring(x, y, radius, radius + (highlight ? 2f : 1.4f), ring, ring);
			if (ability == this.focused) {
				node.arc(x, y, radius + 3f, radius + 4f, time * 0.05f, time * 0.05f + Mth.PI * 1.2f, 0xC0FFFFFF, 0x00FFFFFF);
			}
			node.submit(graphics, false);

			float iconScale = radius / NODE_RADIUS;
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y);
			graphics.pose().scale(iconScale, iconScale);
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ability.icon(), -10, -10, 20, 20, known ? -1 : ARGB.white(0.4f));
			graphics.pose().popMatrix();

			// Stufen-Rauten unter dem Knoten.
			GuiShapes.Mesh pips = GuiShapes.mesh();
			float width = ability.maxLevel() * 6f - 2f;
			for (int p = 0; p < ability.maxLevel(); p++) {
				float px = x - width / 2f + p * 6f + 2f;
				float py = y + radius + 6f;
				int c = p < level ? ARGB.opaque(ARGB.linearLerp(0.4f, color, 0xFFFFFF)) : 0xFF2A3040;
				pips.quad(px, py - 2f, c, px - 2f, py, c, px, py + 2f, c, px + 2f, py, c);
			}
			// "+" zeigt: hier kann man jetzt etwas lernen.
			if (affordable) {
				float bx = x + radius * 0.75f;
				float by = y - radius * 0.75f;
				float pulse = 0.7f + 0.3f * Mth.sin(time * 0.3f);
				pips.disc(bx, by, 4.5f, ARGB.color((int) (255 * pulse), 0x3FD068), ARGB.color((int) (255 * pulse), 0x22A048));
				pips.rect(bx - 2.5f, by - 0.6f, bx + 2.5f, by + 0.6f, 0xFFFFFFFF);
				pips.rect(bx - 0.6f, by - 2.5f, bx + 0.6f, by + 2.5f, 0xFFFFFFFF);
			}
			pips.submit(graphics, false);
		}
	}

	/** Das Fähigkeitenrad im Kleinen: hier legt man fest, was beim Druck auf C zur Wahl steht. */
	private void extractWheel(GuiGraphicsExtractor graphics, ForceData data, int mouseX, int mouseY, float time) {
		float cx = this.wheelX;
		float cy = this.wheelY;
		int hoveredSlot = this.slotAt(mouseX, mouseY);

		Component title = Component.translatable("screen.swmod.holocron.wheel");
		graphics.centeredText(this.font, title, (int) cx, (int) (cy - this.wheelRadius - 26), 0xFFB8C8E0);
		Component key = Component.translatable("screen.swmod.holocron.wheel_key", ForceInput.WHEEL.getTranslatedKeyMessage());
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy - this.wheelRadius - 16);
		graphics.pose().scale(0.75f, 0.75f);
		graphics.centeredText(this.font, key, 0, 0, 0xFF707C90);
		graphics.pose().popMatrix();

		GuiShapes.Mesh mesh = GuiShapes.mesh();
		mesh.ring(cx, cy, this.wheelRadius - 1f, this.wheelRadius + 1f, 0x60405070, 0x60405070);
		mesh.disc(cx, cy, 8f, 0xE0101828, 0xE0182030);
		mesh.submit(graphics, false);

		for (int slot = 0; slot < ForceData.WHEEL_SLOTS; slot++) {
			float angle = ForceInput.slotAngle(slot);
			float x = cx + Mth.sin(angle) * this.wheelRadius;
			float y = cy - Mth.cos(angle) * this.wheelRadius;
			ForceAbility ability = data.abilityInSlot(slot);
			boolean target = slot == hoveredSlot;
			int color = ability != null ? ability.color() : 0x404858;

			GuiShapes.Mesh glow = GuiShapes.mesh();
			if (target && (this.dragging != null || this.focused != null)) {
				int c = this.dragging != null ? this.dragging.color() : 0xFFFFFF;
				glow.glow(x, y, SLOT_RADIUS * 2.4f, ARGB.color((int) (140 + 60 * Mth.sin(time * 0.4f)), c));
			}
			glow.submit(graphics, true);

			GuiShapes.Mesh node = GuiShapes.mesh();
			node.disc(x, y, SLOT_RADIUS, 0xF00C1220, 0xF0141C2C);
			node.ring(x, y, SLOT_RADIUS, SLOT_RADIUS + 1.2f, ARGB.opaque(color), ARGB.opaque(color));
			if (slot == data.selected() && ability != null) {
				node.ring(x, y, SLOT_RADIUS + 2f, SLOT_RADIUS + 2.8f, 0xFFFFFFFF, 0xFFFFFFFF);
			}
			node.submit(graphics, false);

			if (ability != null) {
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ability.icon(), (int) x - 7, (int) y - 7, 14, 14);
			} else {
				String number = Integer.toString(slot + 1);
				graphics.text(this.font, number, (int) x - this.font.width(number) / 2 + 1, (int) y - 3, 0xFF505A6C, false);
			}
		}

		Component hint = Component.translatable(this.dragging != null ? "screen.swmod.holocron.drop" : "screen.swmod.holocron.drag");
		List<FormattedCharSequence> lines = this.font.split(hint, (int) (this.wheelRadius * 2 + 40));
		int line = 0;
		for (FormattedCharSequence sequence : lines) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(cx, cy + this.wheelRadius + 16 + line * 8);
			graphics.pose().scale(0.75f, 0.75f);
			graphics.centeredText(this.font, sequence, 0, 0, 0xFF707C90);
			graphics.pose().popMatrix();
			line++;
		}
	}

	/** Rechte Tafel: alles über die gewählte Fähigkeit und der Knopf zum Lernen. */
	private void extractPanel(GuiGraphicsExtractor graphics, ForceData data, int mouseX, int mouseY, float time) {
		ForceAbility ability = this.focused;
		if (ability == null) {
			return;
		}
		int color = ability.color();
		int level = data.level(ability);
		boolean maxed = level >= ability.maxLevel();
		int x = this.panelX;
		int y = this.panelY;
		Font font = this.font;

		GuiShapes.Mesh panel = GuiShapes.mesh();
		panel.rectV(x, y, x + this.panelW, y + this.panelH, 0xD80A0F1C, 0xE0060A12);
		panel.rect(x, y, x + this.panelW, y + 1, ARGB.opaque(color));
		panel.rectH(x, y + 1, x + this.panelW, y + 2, ARGB.color(160, color), ARGB.color(0, color));
		panel.rect(x, y + this.panelH - 1, x + this.panelW, y + this.panelH, 0x40FFFFFF);
		panel.submit(graphics, false);

		GuiShapes.Mesh glow = GuiShapes.mesh();
		glow.glow(x + 22, y + 22, 26f, ARGB.color(90, color));
		glow.submit(graphics, true);

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 22, y + 22);
		graphics.pose().scale(1.3f, 1.3f);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ability.icon(), -12, -12, 24, 24, level > 0 ? -1 : ARGB.white(0.5f));
		graphics.pose().popMatrix();

		graphics.pose().pushMatrix();
		graphics.pose().translate(x + 42, y + 10);
		graphics.pose().scale(1.15f, 1.15f);
		graphics.text(font, ability.name(), 0, 0, ARGB.opaque(ARGB.linearLerp(0.3f, color, 0xFFFFFF)), true);
		graphics.pose().popMatrix();
		Component side = Component.translatable(ability.side() == ForceSide.LIGHT ? "force.swmod.side.light" : "force.swmod.side.dark");
		graphics.text(font, side, x + 42, y + 22, ARGB.opaque(ability.side().color), false);
		Component levelText = maxed ? Component.translatable("screen.swmod.holocron.mastered").withStyle(ChatFormatting.GOLD)
				: Component.translatable("screen.swmod.holocron.level", level, ability.maxLevel());
		graphics.text(font, levelText, x + 42, y + 32, 0xFFB0B8C8, false);

		int ty = y + 46;
		for (FormattedCharSequence sequence : font.split(ability.description(), this.panelW - 16)) {
			graphics.text(font, sequence, x + 8, ty, 0xFFC8D0DC, false);
			ty += 10;
		}

		Component activation = Component.translatable(ability.activation() == ForceAbility.Activation.CHANNEL
				? "screen.swmod.holocron.hold" : "screen.swmod.holocron.press");
		graphics.text(font, activation, x + 8, ty + 2, 0xFF6FA8D8, false);
		ty += 16;

		// Werte: jetzt -> nach dem Lernen.
		int nextLevel = Math.min(ability.maxLevel(), level + 1);
		for (ForceStat stat : ability.stats()) {
			if (ty > this.buttonY - 12) break;
			graphics.text(font, stat.label().copy().append(":"), x + 8, ty, 0xFF8A94A8, false);
			Component now = level > 0 ? stat.value().apply(level) : Component.literal("–");
			Component value;
			if (!maxed) {
				value = Component.empty().append(now.copy().withStyle(ChatFormatting.WHITE))
						.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
						.append(stat.value().apply(nextLevel).copy().withStyle(ChatFormatting.GREEN));
			} else {
				value = now.copy().withStyle(ChatFormatting.WHITE);
			}
			graphics.text(font, value, x + this.panelW - 8 - font.width(value), ty, 0xFFFFFFFF, false);
			ty += 10;
		}

		this.extractButton(graphics, ability, level, maxed, mouseX, mouseY, time);
	}

	private void extractButton(GuiGraphicsExtractor graphics, ForceAbility ability, int level, boolean maxed, int mouseX, int mouseY, float time) {
		LocalPlayer player = this.minecraft.player;
		int cost = maxed ? 0 : ability.learnCost(level + 1);
		boolean free = player.hasInfiniteMaterials();
		boolean affordable = !maxed && (free || player.experienceLevel >= cost);
		boolean hover = this.overButton(mouseX, mouseY);
		int x = this.buttonX;
		int y = this.buttonY;
		int color = maxed ? 0xFFD45A : affordable ? 0x3FD068 : 0x804850;

		GuiShapes.Mesh button = GuiShapes.mesh();
		int top = ARGB.color(240, ARGB.linearLerp(hover && affordable ? 0.55f : 0.35f, 0x0C1018, color));
		int bottom = ARGB.color(240, ARGB.linearLerp(hover && affordable ? 0.35f : 0.18f, 0x0C1018, color));
		button.rectV(x, y, x + this.buttonW, y + this.buttonH, top, bottom);
		button.rect(x, y, x + this.buttonW, y + 1, ARGB.opaque(color));
		button.rect(x, y + this.buttonH - 1, x + this.buttonW, y + this.buttonH, ARGB.color(200, ARGB.linearLerp(0.5f, color, 0)));
		button.submit(graphics, false);

		if (affordable) {
			GuiShapes.Mesh glow = GuiShapes.mesh();
			float pulse = 0.5f + 0.5f * Mth.sin(time * 0.18f);
			glow.rectH(x, y, x + this.buttonW / 2f, y + this.buttonH, ARGB.color(0, color), ARGB.color((int) (50 + 50 * pulse), color));
			glow.rectH(x + this.buttonW / 2f, y, x + this.buttonW, y + this.buttonH, ARGB.color((int) (50 + 50 * pulse), color), ARGB.color(0, color));
			glow.submit(graphics, true);
		}

		Component label;
		if (maxed) {
			label = Component.translatable("screen.swmod.holocron.mastered");
		} else {
			String key = level == 0 ? "screen.swmod.holocron.learn" : "screen.swmod.holocron.upgrade";
			label = free ? Component.translatable(key + ".free") : Component.translatable(key, cost);
		}
		graphics.centeredText(this.font, label, x + this.buttonW / 2, y + (this.buttonH - 8) / 2, affordable || maxed ? 0xFFFFFFFF : 0xFFFF8A8A);
	}

	private void extractSparks(GuiGraphicsExtractor graphics, float partialTick) {
		if (this.sparks.isEmpty()) {
			return;
		}
		GuiShapes.Mesh glow = GuiShapes.mesh();
		for (Spark spark : this.sparks) {
			float life = Mth.lerp(partialTick, spark.prevLife, spark.life);
			float x = spark.x + spark.vx * partialTick;
			float y = spark.y + spark.vy * partialTick;
			int alpha = (int) (255 * Math.min(1f, life * 1.6f));
			if (spark.star) {
				float s = spark.size * (0.6f + 0.4f * life);
				int c = ARGB.color(alpha, spark.color);
				glow.line(x - s * 2.2f, y, x + s * 2.2f, y, s * 0.5f, c, c);
				glow.line(x, y - s * 2.2f, x, y + s * 2.2f, s * 0.5f, c, c);
				glow.glow(x, y, s * 1.6f, c);
			} else {
				glow.glow(x, y, spark.size * (0.5f + life), ARGB.color(alpha, spark.color));
			}
		}
		glow.submit(graphics, true);
	}

	/** Lichtblitz, Druckwelle und große Schrift beim Lernen. */
	private void extractCelebration(GuiGraphicsExtractor graphics, float time) {
		ForceAbility ability = this.celebrated;
		if (ability == null) {
			return;
		}
		float t = time - this.celebrateStart;
		if (t > 60f) {
			return;
		}
		int index = this.abilities.indexOf(ability);
		float[] pos = this.nodePos(index, time);
		int color = ability.color();

		GuiShapes.Mesh glow = GuiShapes.mesh();
		if (this.flash > 0f) {
			glow.rect(0, 0, this.width, this.height, ARGB.color((int) (110 * this.flash), ARGB.linearLerp(0.5f, color, 0xFFFFFF)));
		}
		for (int wave = 0; wave < 2; wave++) {
			float w = (t - wave * 4f) / 18f;
			if (w > 0f && w < 1f) {
				float r = 10f + w * 70f;
				glow.ring(pos[0], pos[1], r, r + 3f + 6f * (1f - w), ARGB.color((int) (230 * (1f - w)), color), ARGB.color(0, color));
			}
		}
		glow.submit(graphics, true);

		// Titel: schießt groß ins Bild, wippt und verblasst.
		float appear = Mth.clamp(t / 8f, 0f, 1f);
		float fade = t > 45f ? 1f - (t - 45f) / 15f : 1f;
		float scale = 2.2f - 0.6f * ForceWheelHud.easeOutBack(appear);
		Component text = this.celebrateLevel <= 1
				? Component.translatable("screen.swmod.holocron.learned", ability.name())
				: Component.translatable("screen.swmod.holocron.upgraded", ability.name(), this.celebrateLevel);
		int width = this.font.width(text);
		float cx = this.holoX;
		float cy = 34f;

		GuiShapes.Mesh banner = GuiShapes.mesh();
		float bw = width * scale / 2f + 24f;
		banner.rectH(cx - bw, cy - 7 * scale, cx, cy + 9 * scale, ARGB.color(0, color), ARGB.color((int) (120 * fade * appear), color));
		banner.rectH(cx, cy - 7 * scale, cx + bw, cy + 9 * scale, ARGB.color((int) (120 * fade * appear), color), ARGB.color(0, color));
		banner.submit(graphics, true);

		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().scale(scale, scale);
		graphics.text(this.font, text, -width / 2, -4, ARGB.color((int) (255 * fade * appear), 0xFFFFFF), true);
		graphics.pose().popMatrix();
	}

	// ------------------------------------------------------------------------------------------
	// Feiern
	// ------------------------------------------------------------------------------------------

	/** Der Server hat bestätigt: gelernt! Jetzt wird gefeiert. */
	public void celebrate(ForceAbility ability, int level) {
		this.focused = ability;
		this.celebrated = ability;
		this.celebrateLevel = level;
		this.celebrateStart = this.age;
		this.flash = 1f;

		int index = this.abilities.indexOf(ability);
		if (index < 0) {
			return;
		}
		float[] pos = this.nodePos(index, this.age);
		int color = ability.color();
		for (int i = 0; i < 80; i++) {
			float angle = this.random.nextFloat() * Mth.TWO_PI;
			float speed = 1.2f + this.random.nextFloat() * 4.2f;
			Spark spark = new Spark();
			spark.x = pos[0];
			spark.y = pos[1];
			spark.vx = Mth.sin(angle) * speed;
			spark.vy = -Mth.cos(angle) * speed - 0.8f;
			spark.gravity = 0.09f;
			spark.drag = 0.93f;
			spark.maxAge = 22 + this.random.nextInt(26);
			spark.size = 1.2f + this.random.nextFloat() * 2.2f;
			spark.star = this.random.nextFloat() < 0.4f;
			spark.color = this.random.nextFloat() < 0.35f ? 0xFFFFFF : ARGB.linearLerp(this.random.nextFloat() * 0.5f, color, 0xFFFFFF);
			this.sparks.add(spark);
		}
		// Auch der Holocron sprüht.
		for (int i = 0; i < 24; i++) {
			float angle = this.random.nextFloat() * Mth.TWO_PI;
			Spark spark = new Spark();
			spark.x = this.holoX;
			spark.y = this.holoY;
			spark.vx = Mth.sin(angle) * 2.5f;
			spark.vy = -Mth.cos(angle) * 2.5f;
			spark.drag = 0.9f;
			spark.maxAge = 20;
			spark.size = 2f;
			spark.color = 0xBFE6FF;
			this.sparks.add(spark);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Eingaben
	// ------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		ForceData data = ForceClientState.data();

		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.focused != null && this.overButton(mx, my)) {
			this.learn(this.focused, data);
			return true;
		}

		int slot = this.slotAt(mx, my);
		if (slot >= 0) {
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				ClientPlayNetworking.send(new HolocronPayload(HolocronPayload.CLEAR, ForceAbilities.PUSH.id(), slot));
				this.minecraft.player.playSound(ModSounds.FORCE_WHEEL_TICK, 0.5f, 0.6f);
			} else if (this.focused != null && data.knows(this.focused)) {
				this.assign(this.focused, slot);
			}
			return true;
		}

		ForceAbility node = this.nodeAt(mx, my);
		if (node != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			if (this.focused != node) {
				this.minecraft.player.playSound(ModSounds.FORCE_WHEEL_SELECT, 0.4f, 1.3f);
			}
			this.focused = node;
			this.pressed = node;
			this.pressX = mx;
			this.pressY = my;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.pressed != null && this.dragging == null && ForceClientState.data().knows(this.pressed)
				&& Math.abs(event.x() - this.pressX) + Math.abs(event.y() - this.pressY) > 4) {
			this.dragging = this.pressed;
		}
		return this.dragging != null || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.dragging != null) {
			int slot = this.slotAt(event.x(), event.y());
			if (slot >= 0) {
				this.assign(this.dragging, slot);
			}
			this.dragging = null;
			this.pressed = null;
			return true;
		}
		this.pressed = null;
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Zahlen 1-8 legen die gewählte Fähigkeit auf den entsprechenden Radplatz.
		for (int i = 0; i < ForceData.WHEEL_SLOTS; i++) {
			if (this.minecraft.options.keyHotbarSlots[i].matches(event) && this.focused != null && ForceClientState.data().knows(this.focused)) {
				this.assign(this.focused, i);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void learn(ForceAbility ability, ForceData data) {
		LocalPlayer player = this.minecraft.player;
		int level = data.level(ability);
		if (level >= ability.maxLevel()) {
			return;
		}
		if (!player.hasInfiniteMaterials() && player.experienceLevel < ability.learnCost(level + 1)) {
			player.playSound(ModSounds.FORCE_FAIL, 0.6f, 1.0f);
			return;
		}
		ClientPlayNetworking.send(new HolocronPayload(HolocronPayload.LEARN, ability.id(), 0));
		this.charging = ability;
		this.chargeStart = this.age;
	}

	private void assign(ForceAbility ability, int slot) {
		ClientPlayNetworking.send(new HolocronPayload(HolocronPayload.ASSIGN, ability.id(), slot));
		this.minecraft.player.playSound(ModSounds.FORCE_WHEEL_SELECT, 0.6f, 1.0f + slot * 0.05f);
		float angle = ForceInput.slotAngle(slot);
		float x = this.wheelX + Mth.sin(angle) * this.wheelRadius;
		float y = this.wheelY - Mth.cos(angle) * this.wheelRadius;
		for (int i = 0; i < 16; i++) {
			float a = this.random.nextFloat() * Mth.TWO_PI;
			Spark spark = new Spark();
			spark.x = x;
			spark.y = y;
			spark.vx = Mth.sin(a) * 1.6f;
			spark.vy = -Mth.cos(a) * 1.6f;
			spark.drag = 0.88f;
			spark.maxAge = 14;
			spark.size = 1.4f;
			spark.color = ARGB.linearLerp(0.4f, ability.color(), 0xFFFFFF);
			this.sparks.add(spark);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Layout-Helfer
	// ------------------------------------------------------------------------------------------

	private float[] nodePos(int index, float time) {
		float intro = ForceWheelHud.easeOutBack(Mth.clamp((time - index * 1.5f) / 14f, 0f, 1f));
		float angle = index * Mth.TWO_PI / this.abilities.size();
		float radius = this.orbit * intro;
		float bob = Mth.sin(time * 0.05f + index * 1.7f) * 1.5f;
		return new float[]{this.holoX + Mth.sin(angle) * radius, this.holoY - Mth.cos(angle) * radius + bob};
	}

	private @Nullable ForceAbility nodeAt(double mx, double my) {
		for (int i = 0; i < this.abilities.size(); i++) {
			float[] pos = this.nodePos(i, this.age);
			if (Mth.square(mx - pos[0]) + Mth.square(my - pos[1]) <= Mth.square(NODE_RADIUS + 3)) {
				return this.abilities.get(i);
			}
		}
		return null;
	}

	private int slotAt(double mx, double my) {
		for (int slot = 0; slot < ForceData.WHEEL_SLOTS; slot++) {
			float angle = ForceInput.slotAngle(slot);
			float x = this.wheelX + Mth.sin(angle) * this.wheelRadius;
			float y = this.wheelY - Mth.cos(angle) * this.wheelRadius;
			if (Mth.square(mx - x) + Mth.square(my - y) <= Mth.square(SLOT_RADIUS + 2)) {
				return slot;
			}
		}
		return -1;
	}

	private boolean overButton(double mx, double my) {
		return mx >= this.buttonX && mx < this.buttonX + this.buttonW && my >= this.buttonY && my < this.buttonY + this.buttonH;
	}

	private List<Component> tooltip(ForceAbility ability, ForceData data) {
		List<Component> lines = new ArrayList<>();
		lines.add(ability.name().copy().withColor(ability.color()));
		int level = data.level(ability);
		lines.add(level > 0
				? Component.translatable("screen.swmod.holocron.level", level, ability.maxLevel()).withStyle(ChatFormatting.GRAY)
				: Component.translatable("screen.swmod.holocron.unknown").withStyle(ChatFormatting.DARK_GRAY));
		if (level > 0) {
			lines.add(Component.translatable("screen.swmod.holocron.drag_hint").withStyle(ChatFormatting.DARK_AQUA));
		}
		return lines;
	}

	/** Ein Lichtteilchen der Feier-Effekte. */
	private static final class Spark {
		float x;
		float y;
		float vx;
		float vy;
		float gravity;
		float drag = 0.95f;
		float life = 1f;
		float prevLife = 1f;
		int maxAge = 20;
		float size = 1.5f;
		int color = 0xFFFFFF;
		boolean star;
	}
}
