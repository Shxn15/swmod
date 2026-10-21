package com.shxn15.swmod.combat;

import net.minecraft.util.Mth;

/**
 * Lage eines Lichtschwerts im "Blickraum" des Trägers.
 * Ursprung ist die Augenposition, x zeigt nach rechts, y nach oben und z nach vorne (Einheit: Blöcke).
 *
 * @param hx,hy,hz Griffpunkt (dort, wo die Hände den Griff halten)
 * @param dx,dy,dz Klingenrichtung (normiert)
 */
public record SaberPose(float hx, float hy, float hz, float dx, float dy, float dz) {
	public static SaberPose of(float hx, float hy, float hz, float dx, float dy, float dz) {
		float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-5f) {
			return new SaberPose(hx, hy, hz, 0, 1, 0);
		}
		return new SaberPose(hx, hy, hz, dx / len, dy / len, dz / len);
	}

	/** Punkt entlang der Klingenachse, {@code distance} Blöcke vom Griffpunkt entfernt. */
	public float[] along(float distance) {
		return new float[]{this.hx + this.dx * distance, this.hy + this.dy * distance, this.hz + this.dz * distance};
	}

	public SaberPose translate(float x, float y, float z) {
		return new SaberPose(this.hx + x, this.hy + y, this.hz + z, this.dx, this.dy, this.dz);
	}

	/**
	 * Überblendet zwei Posen. Positionen werden linear interpoliert, die Klingenrichtung
	 * sphärisch (slerp), damit auch große Drehungen sauber aussehen.
	 */
	public SaberPose blend(SaberPose to, float t) {
		if (t <= 0) return this;
		if (t >= 1) return to;

		float x = Mth.lerp(t, this.hx, to.hx);
		float y = Mth.lerp(t, this.hy, to.hy);
		float z = Mth.lerp(t, this.hz, to.hz);

		float cos = Mth.clamp(this.dx * to.dx + this.dy * to.dy + this.dz * to.dz, -1f, 1f);
		if (cos > 0.9995f) {
			return of(x, y, z, Mth.lerp(t, this.dx, to.dx), Mth.lerp(t, this.dy, to.dy), Mth.lerp(t, this.dz, to.dz));
		}

		float angle = (float) Math.acos(cos);
		if (angle > Math.PI - 1.0E-3) {
			// Exakt entgegengesetzt: über eine senkrechte Achse drehen, damit slerp definiert ist.
			float px = -this.dy, py = this.dx, pz = 0;
			if (Math.abs(px) + Math.abs(py) < 1.0E-3f) {
				px = 0;
				py = -this.dz;
				pz = this.dy;
			}
			SaberPose mid = of(x, y, z, px, py, pz);
			return t < 0.5f ? this.blend(mid, t * 2).withPosition(x, y, z) : mid.blend(to, (t - 0.5f) * 2).withPosition(x, y, z);
		}

		float sin = Mth.sin(angle);
		float a = Mth.sin((1 - t) * angle) / sin;
		float b = Mth.sin(t * angle) / sin;
		return of(x, y, z, this.dx * a + to.dx * b, this.dy * a + to.dy * b, this.dz * a + to.dz * b);
	}

	public SaberPose withPosition(float x, float y, float z) {
		return new SaberPose(x, y, z, this.dx, this.dy, this.dz);
	}

	/** Dreht die Klinge um die Achse (ax, ay, az) (muss normiert sein) um {@code radians}. */
	public SaberPose rotateBlade(float ax, float ay, float az, float radians) {
		float c = Mth.cos(radians);
		float s = Mth.sin(radians);
		float dot = ax * this.dx + ay * this.dy + az * this.dz;
		// Rodrigues-Rotation
		float cx = ay * this.dz - az * this.dy;
		float cy = az * this.dx - ax * this.dz;
		float cz = ax * this.dy - ay * this.dx;
		return of(this.hx, this.hy, this.hz,
				this.dx * c + cx * s + ax * dot * (1 - c),
				this.dy * c + cy * s + ay * dot * (1 - c),
				this.dz * c + cz * s + az * dot * (1 - c));
	}

	/** Winkel zwischen zwei Klingenrichtungen in Grad. */
	public float angleTo(SaberPose other) {
		float cos = Mth.clamp(this.dx * other.dx + this.dy * other.dy + this.dz * other.dz, -1f, 1f);
		return (float) Math.toDegrees(Math.acos(cos));
	}
}
