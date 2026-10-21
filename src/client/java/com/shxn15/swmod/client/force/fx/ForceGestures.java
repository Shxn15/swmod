package com.shxn15.swmod.client.force.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.force.ForceAbilities;

/**
 * Wirk-Gesten: Die Zweithand stößt nach vorne (Machtstoß), reißt zurück (Machtzug), bleibt beim
 * Halten einer Fähigkeit auf das Ziel gerichtet (beim Würgen leicht erhoben und zitternd) und
 * hebt sich beim Heilen. In der Ego-Perspektive erscheint dafür die linke Hand im Bild.
 */
public final class ForceGestures {
	private ForceGestures() {
	}

	/** Wie weit die Geste gerade ausgeführt ist (0 = Ruhe, 1 = voll). */
	public static float weight(ForceFx.Gesture gesture, float elapsed) {
		if (gesture.channel()) {
			return Mth.clamp(elapsed / 3f, 0f, 1f);
		}
		float t = Mth.clamp(elapsed / Math.max(1, gesture.duration()), 0f, 1f);
		if (gesture.ability() == ForceAbilities.HEAL) {
			return t < 0.3f ? ease(t / 0.3f) : t < 0.7f ? 1f : 1f - ease((t - 0.7f) / 0.3f);
		}
		return t < 0.22f ? ease(t / 0.22f) : t < 0.55f ? 1f : 1f - ease((t - 0.55f) / 0.45f);
	}

	/**
	 * Third Person: aus {@code HumanoidModelMixin}.
	 *
	 * @param holdingSaber beide Hände halten gerade ein Lichtschwert (dann bewegt sich nur die Zweithand)
	 */
	public static void apply(HumanoidModel<?> model, HumanoidRenderState state, ForceRenderData data, boolean holdingSaber) {
		ForceFx.Gesture gesture = data.gesture;
		if (gesture == null) {
			return;
		}
		float w = weight(gesture, data.gestureTime);
		if (w <= 0f) {
			return;
		}

		boolean rightHanded = state.mainArm == HumanoidArm.RIGHT;
		ModelPart arm = rightHanded ? model.leftArm : model.rightArm;
		float side = rightHanded ? 1f : -1f;
		float pitch = state.xRot * Mth.DEG_TO_RAD;
		float yaw = state.yRot * Mth.DEG_TO_RAD;
		float t = data.gestureTime;

		if (gesture.ability() == ForceAbilities.HEAL) {
			// Die Hände öffnen sich nach vorne-oben (mit Lichtschwert nur die freie).
			if (!holdingSaber || !rightHanded) lerpArm(model.rightArm, w, -1.25f, 0f, 0.55f);
			if (!holdingSaber || rightHanded) lerpArm(model.leftArm, w, -1.25f, 0f, -0.55f);
			return;
		}

		float xRot = -Mth.HALF_PI + pitch;
		float yRot = yaw + side * 0.12f;
		float zRot = 0f;
		float reach = 0f;

		if (gesture.ability() == ForceAbilities.PUSH) {
			// Kräftiger Stoß mit dem Handballen nach vorne.
			reach = 2.5f * w;
		} else if (gesture.ability() == ForceAbilities.PULL) {
			// Ausstrecken und ruckartig zurückreißen.
			float u = Mth.clamp(t / Math.max(1, gesture.duration()), 0f, 1f);
			reach = u < 0.25f ? 2f * w : -1.5f * w;
			xRot += u < 0.25f ? 0f : 0.5f * w;
		} else if (gesture.ability() == ForceAbilities.GRIP) {
			// Erhobene, zitternde Klauenhand.
			xRot -= 0.3f;
			xRot += Mth.sin(t * 2.3f) * 0.03f;
			zRot += Mth.sin(t * 3.1f) * 0.03f;
		} else if (gesture.ability() == ForceAbilities.LIGHTNING) {
			xRot += Mth.sin(t * 4.7f) * 0.05f;
			yRot += Mth.sin(t * 3.3f) * 0.05f;
		}

		lerpArm(arm, w, xRot, yRot, zRot);
		arm.z -= reach;
	}

	private static void lerpArm(ModelPart arm, float w, float xRot, float yRot, float zRot) {
		arm.xRot = Mth.lerp(w, arm.xRot, xRot);
		arm.yRot = Mth.lerp(w, arm.yRot, yRot);
		arm.zRot = Mth.lerp(w, arm.zRot, zRot);
	}

	// ------------------------------------------------------------------------------------------
	// Ego-Perspektive
	// ------------------------------------------------------------------------------------------

	/** Die laufende Geste des eigenen Spielers, falls die Hand gerade im Bild sein soll. */
	public static ForceFx.@Nullable Gesture localGesture() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) return null;
		ForceFx.Gesture gesture = ForceFx.gesture(minecraft.player.getId());
		return gesture == null || gesture.ability() == ForceAbilities.SPEED ? null : gesture;
	}

	/**
	 * Zeichnet die Zweithand in der Ego-Perspektive: aus der Ruhe unten links nach vorne zur
	 * Bildmitte gestreckt, Handfläche nach vorne.
	 */
	public static void renderFirstPersonArm(PlayerRenderState playerState, PoseStack poseStack, SubmitNodeCollector collector, int light,
											HumanoidArm arm, float partialTick) {
		ForceFx.Gesture gesture = localGesture();
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (gesture == null || avatar == null || avatar.isInvisible) {
			return;
		}
		float elapsed = ForceClientState.ticks() - gesture.start() + partialTick;
		float w = weight(gesture, elapsed);
		if (w <= 0.01f) {
			return;
		}

		float invert = arm == HumanoidArm.RIGHT ? 1f : -1f;
		float shake = gesture.ability() == ForceAbilities.GRIP || gesture.ability() == ForceAbilities.LIGHTNING
				? Mth.sin(elapsed * 3.7f) * 0.008f : 0f;
		float thrust = gesture.ability() == ForceAbilities.PUSH ? 0.12f * w : 0f;

		poseStack.pushPose();
		poseStack.translate(invert * Mth.lerp(w, 0.9f, 0.42f) + shake, Mth.lerp(w, -1.1f, -0.42f) + shake,
				Mth.lerp(w, -0.75f, -0.82f) - thrust);
		poseStack.rotateDegrees(Axis.YP, invert * Mth.lerp(w, 45f, 18f));
		poseStack.translate(invert * -1.0f, 3.6f, 3.5f);
		poseStack.rotateDegrees(Axis.ZP, invert * 120f);
		poseStack.rotateDegrees(Axis.XP, 200f);
		poseStack.rotateDegrees(Axis.YP, invert * -135f);
		poseStack.translate(invert * 5.6f, 0f, 0f);
		poseStack.rotateDegrees(Axis.XP, Mth.lerp(w, 0f, -12f));

		AvatarRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(avatar);
		Identifier skin = avatar.skin.body().texturePath();
		if (arm == HumanoidArm.RIGHT) {
			renderer.renderRightHand(poseStack, collector, light, skin, avatar.showRightSleeve);
		} else {
			renderer.renderLeftHand(poseStack, collector, light, skin, avatar.showLeftSleeve);
		}
		poseStack.popPose();
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t * t * (3f - 2f * t);
	}
}
