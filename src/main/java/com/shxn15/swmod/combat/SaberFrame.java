package com.shxn15.swmod.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Wandelt Punkte aus dem Blickraum (rechts/oben/vorne relativ zu den Augen) in Weltkoordinaten um.
 */
public record SaberFrame(Vec3 eye, Vec3 forward, Vec3 right, Vec3 up) {
	public static SaberFrame of(Vec3 eye, float yawDegrees, float pitchDegrees) {
		float yaw = yawDegrees * Mth.DEG_TO_RAD;
		float pitch = pitchDegrees * Mth.DEG_TO_RAD;
		float cy = Mth.cos(yaw);
		float sy = Mth.sin(yaw);
		float cp = Mth.cos(pitch);
		float sp = Mth.sin(pitch);

		Vec3 forward = new Vec3(-sy * cp, -sp, cy * cp);
		Vec3 right = new Vec3(-cy, 0, -sy);
		Vec3 up = new Vec3(-sy * sp, cp, cy * sp);
		return new SaberFrame(eye, forward, right, up);
	}

	public static SaberFrame of(Entity entity) {
		return of(entity.getEyePosition(), entity.getYRot(), entity.getXRot());
	}

	public Vec3 toWorld(float x, float y, float z) {
		return new Vec3(
				this.eye.x + this.right.x * x + this.up.x * y + this.forward.x * z,
				this.eye.y + this.right.y * x + this.up.y * y + this.forward.y * z,
				this.eye.z + this.right.z * x + this.up.z * y + this.forward.z * z);
	}

	public Vec3 toWorld(float[] point) {
		return this.toWorld(point[0], point[1], point[2]);
	}

	/** Anfang (Emitter) der leuchtenden Klinge in Weltkoordinaten. */
	public Vec3 bladeBase(SaberPose pose) {
		return this.toWorld(pose.along(SaberGeometry.GRIP_TO_EMITTER));
	}

	/** Spitze der Klinge in Weltkoordinaten. */
	public Vec3 bladeTip(SaberPose pose, float extension) {
		return this.toWorld(pose.along(SaberGeometry.GRIP_TO_EMITTER + SaberGeometry.BLADE_LENGTH * extension));
	}
}
