package com.shxn15.swmod.client.render;

import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;

import net.minecraft.client.player.AbstractClientPlayer;
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
 */
public final class FirstPersonSaberRenderer {
	private static final ItemStackRenderState HILT = new ItemStackRenderState();

	private FirstPersonSaberRenderer() {
	}

	public static void render(AbstractClientPlayer player, ItemStack stack, float partialTick, float equipProgress,
							  PoseStack poseStack, SubmitNodeCollector collector, int light) {
		SaberAnimState anim = SaberAnimations.get(player);
		if (anim.isThrown()) {
			return;
		}

		float time = SaberAnimations.time(partialTick);
		float extension = anim.extension(partialTick);
		boolean mirror = player.getMainArm() == HumanoidArm.LEFT;
		// Beim Ziehen/Wechseln des Items kommt das Schwert von unten ins Bild.
		float drop = equipProgress * 0.6f;

		Function<Vector3f, Vector3f> toCamera = v -> new Vector3f(mirror ? -v.x : v.x, v.y - drop, -v.z);
		Function<Vector3f, Vector3f> dirToCamera = v -> new Vector3f(mirror ? -v.x : v.x, v.y, -v.z);
		SaberPoseSampler.Sample sample = SaberPoseSampler.sample(anim, time, extension, true, toCamera, dirToCamera);

		HiltModels.update(HILT, stack, player);
		SaberRenderer.submitHilt(poseStack, collector, HILT, sample.grip(), sample.direction(), 0f, light);

		if (extension > 0.001f) {
			int color = Lightsabers.color(stack);
			SaberRenderer.submitTrail(poseStack, collector, sample.trailBases(), sample.trailTips(), sample.trailAlphas(), color);
			SaberRenderer.submitBlade(poseStack, collector, sample.emitter(), sample.tip(), color, 1f, time);
		}
	}
}
