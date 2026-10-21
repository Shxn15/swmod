package com.shxn15.swmod.client.force.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.client.gui.GuiShapes;
import com.shxn15.swmod.force.ForceAbilities;

/**
 * Effekte direkt auf dem Bildschirm des eigenen Spielers: Geschwindigkeitslinien bei
 * Machtgeschwindigkeit, rote Vignette beim Gewürgtwerden, Flackern bei Blitztreffern, grünes
 * Leuchten mit aufsteigenden Funken beim Heilen, eine Druckwelle beim Stoßen und Ziehen und
 * Kamerawackeln bei Stößen und Aufprallen.
 */
public final class ForceScreenEffects {
	private static final int LINE_COUNT = 56;
	private static final float[][] LINES = new float[LINE_COUNT][3];
	/** Heil-Funken: Lage im Randstreifen, Steiggeschwindigkeit, Größe, Phase, Seite. */
	private static final float[][] HEAL_SPARKS = new float[18][5];
	private static final float WAVE_TICKS = 7f;

	private static float shake;
	private static float prevShake;
	private static float grip;
	private static float prevGrip;
	private static float shock;
	private static float prevShock;
	private static float heal;
	private static float prevHeal;
	private static float speed;
	private static float prevSpeed;
	private static float burst;
	private static float prevBurst;
	private static int healStart = -100;
	private static int waveStart = -100;
	private static boolean waveInward;

	static {
		RandomSource random = RandomSource.create(4242);
		for (float[] line : LINES) {
			line[0] = random.nextFloat() * Mth.TWO_PI;
			line[1] = random.nextFloat();
			line[2] = 0.6f + random.nextFloat() * 0.8f;
		}
		for (float[] spark : HEAL_SPARKS) {
			spark[0] = random.nextFloat();
			spark[1] = 3.5f + random.nextFloat() * 5f;
			spark[2] = 2.5f + random.nextFloat() * 2.5f;
			spark[3] = random.nextFloat() * Mth.TWO_PI;
			spark[4] = random.nextFloat();
		}
	}

	private ForceScreenEffects() {
	}

	public static void init() {
		HudElementRegistry.addFirst(SwMod.id("force_screen_effects"), ForceScreenEffects::extract);
	}

	public static void shake(float amount) {
		shake = Math.min(1.6f, shake + amount);
	}

	public static void gripped() {
		grip = Math.min(1f, grip + 0.2f);
	}

	public static void shocked() {
		shock = Math.min(1f, shock + 0.35f);
		shake = Math.min(1.6f, shake + 0.05f);
	}

	public static void healFlash() {
		heal = 1f;
		healStart = ForceClientState.ticks();
	}

	/** Eine Druckwelle über den Bildschirm: nach außen beim Stoß, nach innen beim Zug. */
	public static void wave(boolean inward) {
		waveStart = ForceClientState.ticks();
		waveInward = inward;
	}

	public static void speedBurst() {
		burst = 1f;
	}

	public static void tick(Minecraft minecraft) {
		prevShake = shake;
		prevGrip = grip;
		prevShock = shock;
		prevHeal = heal;
		prevSpeed = speed;
		prevBurst = burst;
		shake = Math.max(0f, shake * 0.82f - 0.01f);
		grip = Math.max(0f, grip - 0.08f);
		shock = Math.max(0f, shock - 0.15f);
		heal = Math.max(0f, heal - 0.04f);
		burst = Math.max(0f, burst - 0.07f);

		LocalPlayer player = minecraft.player;
		float target = 0f;
		if (player != null && ForceFx.isSpeeding(player)) {
			Vec3 motion = player.position().subtract(player.xo, player.yo, player.zo);
			target = Mth.clamp((float) motion.horizontalDistance() / 0.45f, 0.25f, 1f);
		}
		speed = Mth.lerp(0.2f, speed, target);
	}

	/** Kamerawackeln, aus {@code GameRendererMixin} (nach dem Wackeln bei Schaden). */
	public static void applyShake(PoseStack poseStack) {
		float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float amount = Mth.lerp(partialTick, prevShake, shake);
		if (amount <= 0.001f) {
			return;
		}
		float time = ForceClientState.ticks() + partialTick;
		poseStack.rotateDegrees(Axis.ZP, Mth.sin(time * 2.9f) * amount * 1.6f);
		poseStack.rotateDegrees(Axis.XP, Mth.sin(time * 3.7f + 1.3f) * amount * 1.1f);
	}

	private static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.gui.hud.isHidden()) {
			return;
		}
		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
		float time = ForceClientState.ticks() + partialTick;
		float w = graphics.guiWidth();
		float h = graphics.guiHeight();
		float cx = w / 2f;
		float cy = h / 2f;
		float radius = Mth.sqrt(cx * cx + cy * cy);

		float speedAmount = Mth.lerp(partialTick, prevSpeed, speed);
		float burstAmount = Mth.lerp(partialTick, prevBurst, burst);
		if (speedAmount > 0.01f || burstAmount > 0.01f) {
			GuiShapes.Mesh lines = GuiShapes.mesh();
			float intensity = Math.min(1f, speedAmount + burstAmount * 0.6f);
			for (float[] line : LINES) {
				// Linien rasen vom Rand zur Mitte und verblassen dort.
				float phase = (line[1] + time * 0.09f * line[2]) % 1f;
				float r0 = radius * (1.05f - phase * 0.55f);
				float r1 = r0 - radius * (0.12f + 0.18f * intensity) * line[2];
				float dx = Mth.sin(line[0]);
				float dy = -Mth.cos(line[0]);
				int alpha = (int) (150 * intensity * Mth.sin(phase * Mth.PI));
				lines.softLine(cx + dx * r0, cy + dy * r0, cx + dx * r1, cy + dy * r1, 1.6f + 1.2f * line[2],
						ARGB.color(alpha, 0xE8F6FF), ARGB.color(0, 0xBFE4FF));
			}
			lines.ring(cx, cy, radius * 0.62f, radius * 1.05f, ARGB.color(0, ForceAbilities.SPEED.color()),
					ARGB.color((int) (70 * intensity), 0x9FD8FF));
			lines.submit(graphics, true);
		}

		float gripAmount = Mth.lerp(partialTick, prevGrip, grip);
		if (gripAmount > 0.01f) {
			float pulse = 0.75f + 0.25f * Mth.sin(time * 0.5f);
			GuiShapes.Mesh vignette = GuiShapes.mesh();
			vignette.ring(cx, cy, radius * 0.45f, radius * 1.05f, ARGB.color(0, 0x500000), ARGB.color((int) (210 * gripAmount * pulse), 0x400000));
			vignette.submit(graphics, false);
			GuiShapes.Mesh glow = GuiShapes.mesh();
			glow.ring(cx, cy, radius * 0.55f, radius * 1.05f, ARGB.color(0, 0xFF2020), ARGB.color((int) (90 * gripAmount * pulse), 0xFF2020));
			glow.submit(graphics, true);
		}

		float shockAmount = Mth.lerp(partialTick, prevShock, shock);
		if (shockAmount > 0.01f) {
			RandomSource random = RandomSource.create((long) (time * 3));
			float flicker = 0.5f + random.nextFloat() * 0.5f;
			GuiShapes.Mesh glow = GuiShapes.mesh();
			glow.ring(cx, cy, radius * 0.5f, radius * 1.05f, ARGB.color(0, 0xB98CFF), ARGB.color((int) (130 * shockAmount * flicker), 0xC8B0FF));
			glow.submit(graphics, true);
		}

		float healAmount = Mth.lerp(partialTick, prevHeal, heal);
		if (healAmount > 0.01f) {
			GuiShapes.Mesh glow = GuiShapes.mesh();
			glow.ring(cx, cy, radius * 0.5f, radius * 1.05f, ARGB.color(0, 0x6CFF9A), ARGB.color((int) (110 * healAmount), 0x9CFFB0));
			// Goldene und grüne Funken steigen an den Bildrändern auf.
			float age = time - healStart;
			for (int i = 0; i < HEAL_SPARKS.length; i++) {
				float[] spark = HEAL_SPARKS[i];
				float band = w * 0.22f;
				float x = spark[4] < 0.5f ? spark[0] * band : w - spark[0] * band;
				float y = h + 6f - age * spark[1];
				float twinkle = 0.6f + 0.4f * Mth.sin(age * 0.8f + spark[3]);
				int color = i % 3 == 0 ? 0xFFF2B0 : 0xB8FFC8;
				int a = (int) (230 * Math.min(1f, healAmount * 1.6f) * twinkle);
				glow.star(x, y, spark[2] * 2.2f * twinkle, ARGB.color(a, color));
				glow.glow(x, y, spark[2] * 1.8f, ARGB.color(a / 2, color));
			}
			glow.submit(graphics, true);
		}

		float waveProgress = (time - waveStart) / WAVE_TICKS;
		if (waveProgress >= 0f && waveProgress < 1f) {
			// Außen: schnell und dann langsamer; innen: zieht sich zur Mitte zusammen.
			float eased = 1f - (1f - waveProgress) * (1f - waveProgress);
			float r = radius * (waveInward ? 1.05f - 0.95f * eased : 0.08f + 0.97f * eased);
			int a = (int) (120 * (1f - waveProgress));
			int color = waveInward ? ForceAbilities.PULL.color() : ForceAbilities.PUSH.color();
			GuiShapes.Mesh wave = GuiShapes.mesh();
			if (waveInward) {
				wave.ring(cx, cy, Math.max(0f, r - 5f), r, ARGB.color(0, 0xFFFFFF), ARGB.color(a, 0xFFFFFF));
				wave.ring(cx, cy, r, r + 30f, ARGB.color(a, color), ARGB.color(0, color));
			} else {
				wave.ring(cx, cy, Math.max(0f, r - 30f), r, ARGB.color(0, color), ARGB.color(a, color));
				wave.ring(cx, cy, r, r + 5f, ARGB.color(a, 0xFFFFFF), ARGB.color(0, 0xFFFFFF));
			}
			wave.submit(graphics, true);
		}
	}

	public static void clear() {
		shake = prevShake = grip = prevGrip = shock = prevShock = heal = prevHeal = speed = prevSpeed = burst = prevBurst = 0f;
		healStart = waveStart = -100;
	}
}
