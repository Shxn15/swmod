package com.shxn15.swmod.client.render;

import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.client.anim.SaberAnimations;
import com.shxn15.swmod.lightsaber.Lightsabers;

/**
 * Third-Person: berechnet beim Extrahieren des Render-States die Schwertpose im Modellraum
 * und richtet später die Arme so aus, dass die Hände den Griff halten.
 *
 * <p>Modellraum von Spielern: Ursprung ca. auf Nackenhöhe (1,501 Modelleinheiten über den Füßen),
 * +y zeigt nach unten, -z nach vorne, -x zur rechten Hand. Eine Modelleinheit sind 0,9375 Blöcke.
 */
public final class ThirdPersonSaber {
	private static final float PLAYER_MODEL_SCALE = 0.9375f;
	private static final float MODEL_ORIGIN_HEIGHT = 1.501f;
	private static final float CROUCH_OFFSET = 0.125f;

	private ThirdPersonSaber() {
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTick) {
		SaberRenderData data = ((SaberRenderData.Access) state).swmod$saberData();
		data.active = false;
		data.sample = null;

		if (!(entity instanceof Avatar)) {
			return;
		}

		ItemStack stack = entity.getMainHandItem();
		SaberAnimState anim = SaberAnimations.peek(entity);
		if (!Lightsabers.isLightsaber(stack) || anim == null || anim.isThrown()) {
			return;
		}

		float time = SaberAnimations.time(partialTick);
		float extension = anim.extension(partialTick);
		boolean mirror = entity.getMainArm() == HumanoidArm.LEFT;

		// Kopfdrehung relativ zum Körper, wie beim Kopf-Modellteil.
		Quaternionf head = new Quaternionf().rotationZYX(0f, state.yRot * Mth.DEG_TO_RAD, state.xRot * Mth.DEG_TO_RAD);
		float eyeHeight = entity.getEyeHeight() + (entity.isCrouching() ? CROUCH_OFFSET : 0f);
		float eyeY = -(eyeHeight / PLAYER_MODEL_SCALE - MODEL_ORIGIN_HEIGHT);

		// Blickraum (rechts, oben, vorne) -> Modellraum (-x, -y, -z), dann Kopfdrehung.
		Function<Vector3f, Vector3f> dirToModel = v -> head.transform(new Vector3f(mirror ? v.x : -v.x, -v.y, -v.z));
		Function<Vector3f, Vector3f> toModel = v -> dirToModel.apply(v).div(PLAYER_MODEL_SCALE).add(0f, eyeY, 0f);

		data.sample = SaberPoseSampler.sample(anim, time, extension, false, toModel, dirToModel);
		data.color = Lightsabers.color(stack);
		data.extension = extension;
		data.time = time;
		data.active = true;
		HiltModels.update(data.hilt, stack, entity);
	}

	/** Richtet einen Arm so aus, dass er vom Schultergelenk zum Griff zeigt. */
	public static void pointArmAt(ModelPart arm, Vector3f target) {
		float px = arm.x / 16f;
		float py = arm.y / 16f;
		float pz = arm.z / 16f;
		float ax = target.x - px;
		float ay = target.y - py;
		float az = target.z - pz;
		float length = Mth.sqrt(ax * ax + ay * ay + az * az);
		if (length < 1.0E-4f) {
			return;
		}
		ax /= length;
		ay /= length;
		az /= length;

		// Ruhelage des Arms ist +y; Rotation Rz * Rx bildet (0,1,0) auf (ax, ay, az) ab.
		arm.xRot = (float) Math.asin(Mth.clamp(az, -1f, 1f));
		arm.yRot = 0f;
		arm.zRot = (float) Math.atan2(-ax, ay);
	}
}
