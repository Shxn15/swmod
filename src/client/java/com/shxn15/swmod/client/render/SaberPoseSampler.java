package com.shxn15.swmod.client.render;

import java.util.function.Function;

import org.joml.Vector3f;

import net.minecraft.util.Mth;

import com.shxn15.swmod.client.anim.SaberAnimState;
import com.shxn15.swmod.combat.SaberGeometry;
import com.shxn15.swmod.combat.SaberPose;

/**
 * Wandelt Posen aus dem Blickraum in den Raum eines Renderers um und tastet die jüngste
 * Vergangenheit ab, um einen Bewegungsschweif zu bilden.
 */
public final class SaberPoseSampler {
	/** Wie weit der Schweif in die Vergangenheit reicht (Ticks) und wie fein abgetastet wird. */
	private static final int TRAIL_SAMPLES = 12;
	private static final float TRAIL_STEP = 0.28f;
	/** Ab dieser Klingengeschwindigkeit (Grad/Tick) wird der Schweif sichtbar. */
	private static final float TRAIL_MIN_SPEED = 9f;

	private SaberPoseSampler() {
	}

	/** Ergebnis für einen Frame. Alle Vektoren im Zielraum. */
	public record Sample(Vector3f grip, Vector3f direction, Vector3f emitter, Vector3f tip,
						 Vector3f[] trailBases, Vector3f[] trailTips, float[] trailAlphas) {
	}

	/**
	 * @param toSpace wandelt einen Punkt aus dem Blickraum in den Zielraum um
	 * @param dirToSpace wandelt eine Richtung aus dem Blickraum in den Zielraum um
	 */
	public static Sample sample(SaberAnimState anim, float time, float extension, boolean firstPerson,
								Function<Vector3f, Vector3f> toSpace, Function<Vector3f, Vector3f> dirToSpace) {
		SaberPose pose = anim.pose(time, firstPerson);
		float bladeLength = SaberGeometry.BLADE_LENGTH * extension;

		Vector3f grip = toSpace.apply(new Vector3f(pose.hx(), pose.hy(), pose.hz()));
		Vector3f direction = dirToSpace.apply(new Vector3f(pose.dx(), pose.dy(), pose.dz())).normalize();
		Vector3f emitter = toSpace.apply(vec(pose.along(SaberGeometry.GRIP_TO_EMITTER)));
		Vector3f tip = toSpace.apply(vec(pose.along(SaberGeometry.GRIP_TO_EMITTER + bladeLength)));

		Vector3f[] bases = new Vector3f[0];
		Vector3f[] tips = new Vector3f[0];
		float[] alphas = new float[0];

		if (extension > 0.5f && anim.action() != null) {
			float speed = anim.bladeSpeed(time);
			if (speed > TRAIL_MIN_SPEED) {
				float strength = Mth.clamp((speed - TRAIL_MIN_SPEED) / 25f, 0f, 1f);
				bases = new Vector3f[TRAIL_SAMPLES];
				tips = new Vector3f[TRAIL_SAMPLES];
				alphas = new float[TRAIL_SAMPLES];
				for (int i = 0; i < TRAIL_SAMPLES; i++) {
					SaberPose past = anim.pose(time - i * TRAIL_STEP, firstPerson);
					bases[i] = toSpace.apply(vec(past.along(SaberGeometry.GRIP_TO_EMITTER + bladeLength * 0.1f)));
					tips[i] = toSpace.apply(vec(past.along(SaberGeometry.GRIP_TO_EMITTER + bladeLength)));
					float fade = 1f - i / (float) (TRAIL_SAMPLES - 1);
					alphas[i] = 0.55f * strength * fade * fade;
				}
			}
		}

		return new Sample(grip, direction, emitter, tip, bases, tips, alphas);
	}

	private static Vector3f vec(float[] point) {
		return new Vector3f(point[0], point[1], point[2]);
	}
}
