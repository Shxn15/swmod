package com.shxn15.swmod.client.render;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.lightsaber.Lightsabers;

/**
 * Lichtschwert in der Ego-Perspektive. Die Pose kommt aus demselben Animationssystem wie
 * Third-Person und Trefferprüfung, nur in den Kameraraum umgerechnet (dort zeigt -z nach vorne).
 *
 * <p>Wie Vanilla getrennt in "extrahieren" (Daten sammeln) und "submit" (zeichnen).
 */
public final class FirstPersonSaberRenderer {
	private static final ItemStackRenderState HILT = new ItemStackRenderState();
	private static SaberPoseSampler.@Nullable Sample sample;
	private static int color;
	private static float extension;
	private static float time;
	private static boolean mirrored;

	private FirstPersonSaberRenderer() {
	}

	/** Aus {@code FirstPersonHandsAndItems#extractRenderState}. */
	public static void extract(LocalPlayer player, ItemStack stack, float partialTick) {
		sample = null;
		if (!Lightsabers.isLightsaber(stack)) {
			return;
		}

		SaberAnimState anim = SaberAnimations.get(player);
		if (anim.isThrown()) {
			return;
		}

		time = SaberAnimations.time(partialTick);
		extension = anim.extension(partialTick);
		color = Lightsabers.color(stack);
		mirrored = player.getMainArm() == HumanoidArm.LEFT;

		boolean mirror = mirrored;
		Function<Vector3f, Vector3f> toCamera = v -> new Vector3f(mirror ? -v.x : v.x, v.y, -v.z);
		sample = SaberPoseSampler.sample(anim, time, extension, true, toCamera, toCamera);
		HiltModels.update(HILT, stack, player);
	}

	/**
	 * Aus {@code FirstPersonHandsAndItemsRenderer#submitArmWithItem}.
	 *
	 * @param inverseArmHeight 0 = ganz im Bild, 1 = beim Itemwechsel ganz unten
	 */
	public static void submit(float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light) {
		SaberPoseSampler.Sample current = sample;
		if (current == null) {
			return;
		}

		poseStack.pushPose();
		// Beim Ziehen/Wechseln des Items kommt das Schwert von unten ins Bild.
		poseStack.translate(0f, -0.6f * inverseArmHeight, 0f);
		SaberRenderer.submitHilt(poseStack, collector, HILT, current.grip(), current.direction(), 0f, light);
		if (extension > 0.001f) {
			SaberRenderer.submitTrail(poseStack, collector, current.trailBases(), current.trailTips(), current.trailAlphas(), color);
			SaberRenderer.submitBlade(poseStack, collector, current.emitter(), current.tip(), color, 1f, time);
		}
		poseStack.popPose();
	}
}
