package com.shxn15.swmod.combat;

import net.minecraft.util.Mth;

/**
 * Die "Choreografie" des Lichtschwerts: wo Griff und Klinge in jeder Phase eines Schlags sind.
 * Der Server nutzt dieselben Funktionen für die Trefferprüfung wie der Client für die Animation,
 * damit man genau das trifft, was man sieht.
 */
public final class SaberGeometry {
	private SaberGeometry() {
	}

	/** Abstand Griffpunkt -> Emitter (Beginn der Klinge). */
	public static final float GRIP_TO_EMITTER = 0.17f;
	/** Abstand Griffpunkt -> Knauf (Ende des Griffs). */
	public static final float GRIP_TO_POMMEL = 0.13f;
	public static final float HILT_LENGTH = GRIP_TO_EMITTER + GRIP_TO_POMMEL;
	public static final float BLADE_LENGTH = 1.2f;

	/** Grundhaltung: Klinge schräg nach oben vor dem Körper. */
	public static final SaberPose IDLE = SaberPose.of(0.30f, -0.36f, 0.48f, -0.28f, 0.90f, 0.30f);
	/** Deckung: Klinge quer vor dem Oberkörper. */
	public static final SaberPose BLOCK = SaberPose.of(0.30f, -0.22f, 0.46f, -1.0f, 0.24f, 0.06f);
	/** Taumeln nach einer Parade: Klinge nach hinten oben weggeschlagen. */
	public static final SaberPose STAGGERED = SaberPose.of(0.36f, -0.12f, 0.30f, 0.35f, 0.85f, -0.40f);

	private static final float SWING_CENTER_X = 0.06f;
	private static final float SWING_CENTER_Y = -0.30f;
	private static final float SWING_CENTER_Z = 0.42f;
	private static final float SWING_REACH = 0.24f;
	private static final float ARC_START = (float) Math.toRadians(-110);
	private static final float ARC_END = (float) Math.toRadians(80);

	/**
	 * Pose entlang des Schlagbogens.
	 *
	 * @param progress 0 = voll ausgeholt, 1 = komplett durchgezogen
	 */
	public static SaberPose swing(SwingDirection dir, float progress) {
		if (dir.isStab()) {
			return stab(progress);
		}

		// Die Klinge dreht in der Ebene aus "vorne" und der Schlagrichtung auf dem Bildschirm.
		float phi = Mth.lerp(progress, ARC_START, ARC_END);
		float c = Mth.cos(phi);
		float s = Mth.sin(phi);
		float sx = dir.screenX;
		float sy = dir.screenY;

		float hx = SWING_CENTER_X + sx * SWING_REACH * s;
		float hy = SWING_CENTER_Y + sy * SWING_REACH * s;
		float hz = SWING_CENTER_Z + 0.10f * c;
		return SaberPose.of(hx, hy, hz, sx * s, sy * s, c + 0.15f);
	}

	/** Stoß: 0 = zurückgezogen, 1 = voll ausgestreckt. */
	public static SaberPose stab(float progress) {
		float p = Mth.clamp(progress, 0f, 1f);
		return SaberPose.of(0.14f - 0.08f * p, -0.30f + 0.04f * p, 0.26f + 0.62f * p, -0.04f, -0.03f, 1f);
	}

	/** Pose während der Treffphase; {@code u} läuft linear von 0 bis 1 über die aktiven Ticks. */
	public static SaberPose activePose(SaberMove move, float u) {
		return swing(move.direction(), easeActive(u));
	}

	/**
	 * Vollständige Animation eines Angriffs.
	 *
	 * @param elapsed vergangene Ticks seit Beginn (mit Teil-Ticks)
	 * @param from    Pose beim Start des Angriffs (für weiches Ausholen)
	 * @param rest    Pose, in die nach dem Schlag zurückgekehrt wird
	 */
	public static SaberPose movePose(SaberMove move, float elapsed, SaberPose from, SaberPose rest) {
		SwingDirection dir = move.direction();
		if (elapsed < move.windup()) {
			return from.blend(swing(dir, 0f), easeOutCubic(elapsed / move.windup()));
		}

		float t = elapsed - move.windup();
		if (t < move.active()) {
			return activePose(move, t / move.active());
		}

		t -= move.active();
		if (t < move.recovery()) {
			return swing(dir, 1f).blend(rest, easeInOutCubic(t / move.recovery()));
		}

		return rest;
	}

	/** Schnell in der Mitte, weich an den Enden: so fühlt sich ein Schwerthieb an. */
	public static float easeActive(float u) {
		u = Mth.clamp(u, 0f, 1f);
		return u * u * (3f - 2f * u);
	}

	public static float easeOutCubic(float t) {
		t = Mth.clamp(t, 0f, 1f);
		float inv = 1f - t;
		return 1f - inv * inv * inv;
	}

	public static float easeInOutCubic(float t) {
		t = Mth.clamp(t, 0f, 1f);
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/** Kleiner "Rückstoß" der Klinge, z.B. wenn ein Schlag geblockt wurde. */
	public static SaberPose bounce(SaberPose pose, SwingDirection interrupted, float strength) {
		float ax;
		float ay;
		float az;
		if (interrupted == null || interrupted.isStab()) {
			// Klinge kippt zum Körper zurück (Drehung um die Rechts-Achse).
			ax = 1;
			ay = 0;
			az = 0;
		} else {
			// Drehachse senkrecht zur Schlagebene: Klinge läuft ein Stück gegen die Schlagrichtung zurück.
			ax = -interrupted.screenY;
			ay = interrupted.screenX;
			az = 0;
		}
		return pose.rotateBlade(ax, ay, az, -0.7f * strength)
				.translate(-0.06f * strength * (interrupted == null ? 0 : interrupted.screenX),
						-0.06f * strength * (interrupted == null ? 0 : interrupted.screenY),
						-0.08f * strength);
	}

	/** Kürzester Abstand zwischen den Strecken p1-q1 und p2-q2 (für Klingenzusammenstöße). */
	public static double segmentDistance(net.minecraft.world.phys.Vec3 p1, net.minecraft.world.phys.Vec3 q1,
										 net.minecraft.world.phys.Vec3 p2, net.minecraft.world.phys.Vec3 q2) {
		net.minecraft.world.phys.Vec3 d1 = q1.subtract(p1);
		net.minecraft.world.phys.Vec3 d2 = q2.subtract(p2);
		net.minecraft.world.phys.Vec3 r = p1.subtract(p2);
		double a = d1.dot(d1);
		double e = d2.dot(d2);
		double f = d2.dot(r);
		double s;
		double t;

		if (a <= 1.0E-8 && e <= 1.0E-8) {
			return p1.distanceTo(p2);
		}
		if (a <= 1.0E-8) {
			s = 0;
			t = Mth.clamp(f / e, 0, 1);
		} else {
			double c = d1.dot(r);
			if (e <= 1.0E-8) {
				t = 0;
				s = Mth.clamp(-c / a, 0, 1);
			} else {
				double b = d1.dot(d2);
				double denom = a * e - b * b;
				s = denom != 0 ? Mth.clamp((b * f - c * e) / denom, 0, 1) : 0;
				t = (b * s + f) / e;
				if (t < 0) {
					t = 0;
					s = Mth.clamp(-c / a, 0, 1);
				} else if (t > 1) {
					t = 1;
					s = Mth.clamp((b - c) / a, 0, 1);
				}
			}
		}

		net.minecraft.world.phys.Vec3 c1 = p1.add(d1.scale(s));
		net.minecraft.world.phys.Vec3 c2 = p2.add(d2.scale(t));
		return c1.distanceTo(c2);
	}
}
