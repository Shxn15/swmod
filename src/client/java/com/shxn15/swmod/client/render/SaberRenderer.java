package com.shxn15.swmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import com.shxn15.swmod.combat.SaberGeometry;

/**
 * Zeichnet Griff, leuchtende Klinge und Bewegungsschweif. Alle Koordinaten sind im lokalen Raum
 * des übergebenen PoseStacks. Die Klinge besteht aus mehreren ineinanderliegenden, additiv
 * überblendeten Prismen: ein fast weißer Kern und farbige Leuchthüllen, die nach außen ausfaden.
 */
public final class SaberRenderer {
	private static final int SIDES = 8;

	/** Radius, Weißanteil, Deckkraft je Leuchtschicht (innen nach außen). */
	private static final float[][] LAYERS = {
			{0.011f, 0.92f, 1.00f},
			{0.020f, 0.55f, 0.80f},
			{0.034f, 0.15f, 0.42f},
			{0.058f, 0.00f, 0.20f},
			{0.095f, 0.00f, 0.085f},
			{0.150f, 0.00f, 0.035f},
	};

	private SaberRenderer() {
	}

	/**
	 * Klinge vom Emitter {@code base} bis zur Spitze {@code tip}.
	 *
	 * @param intensity 0..1, z.B. beim Zünden oder für Bewegungsunschärfe
	 * @param time      Animationszeit für das Flackern
	 */
	public static void submitBlade(PoseStack poseStack, SubmitNodeCollector collector, Vector3f base, Vector3f tip,
								   int rgb, float intensity, float time) {
		Vector3f axis = new Vector3f(tip).sub(base);
		float length = axis.length();
		if (length < 1.0E-3f || intensity <= 0.01f) {
			return;
		}
		axis.div(length);

		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;

		// Leichtes, unregelmäßiges Flackern wie bei einer Plasmaklinge.
		float flicker = 0.93f + 0.04f * Mth.sin(time * 2.7f) + 0.03f * Mth.sin(time * 7.3f + 1.3f);
		Vector3f u = new Vector3f();
		Vector3f v = new Vector3f();
		perpendicularBasis(axis, u, v);

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
			for (float[] layer : LAYERS) {
				float radius = layer[0] * (layer[0] > 0.05f ? flicker : 1f);
				float white = layer[1];
				float alpha = layer[2] * intensity * (layer[0] > 0.03f ? flicker : 1f);
				float lr = Mth.lerp(white, r, 1f);
				float lg = Mth.lerp(white, g, 1f);
				float lb = Mth.lerp(white, b, 1f);
				emitCapsule(pose, consumer, base, axis, length, radius, u, v, lr, lg, lb, alpha);
			}
		});
	}

	/**
	 * Schweif hinter einer schnell bewegten Klinge: ein Band zwischen den vergangenen Positionen
	 * von Emitter und Spitze, das nach hinten ausblendet.
	 */
	public static void submitTrail(PoseStack poseStack, SubmitNodeCollector collector, Vector3f[] bases, Vector3f[] tips,
								   float[] alphas, int rgb) {
		if (bases.length < 2) {
			return;
		}

		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;

		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, consumer) -> {
			for (int i = 0; i < bases.length - 1; i++) {
				float a0 = alphas[i];
				float a1 = alphas[i + 1];
				if (a0 <= 0.003f && a1 <= 0.003f) continue;

				// Innen heller und weißer, außen farbig.
				Vector3f b0 = bases[i];
				Vector3f b1 = bases[i + 1];
				Vector3f t0 = tips[i];
				Vector3f t1 = tips[i + 1];
				Vector3f m0 = new Vector3f(b0).lerp(t0, 0.15f);
				Vector3f m1 = new Vector3f(b1).lerp(t1, 0.15f);

				doubleSidedQuad(pose, consumer, m0, t0, t1, m1, r, g, b, a0, a1);
				float wr = Mth.lerp(0.6f, r, 1f);
				float wg = Mth.lerp(0.6f, g, 1f);
				float wb = Mth.lerp(0.6f, b, 1f);
				Vector3f c0 = new Vector3f(b0).lerp(t0, 0.55f);
				Vector3f c1 = new Vector3f(b1).lerp(t1, 0.55f);
				doubleSidedQuad(pose, consumer, c0, t0, t1, c1, wr, wg, wb, a0 * 0.5f, a1 * 0.5f);
			}
		});
	}

	/**
	 * Griff aus dem Item-Modell. Das Modell liegt entlang +Y und ist 1 Block lang;
	 * hier wird es auf die echte Grifflänge skaliert und an der Klingenachse ausgerichtet.
	 */
	public static void submitHilt(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState hilt,
								  Vector3f grip, Vector3f direction, float roll, int light) {
		if (hilt.isEmpty()) {
			return;
		}

		float center = (SaberGeometry.GRIP_TO_EMITTER - SaberGeometry.GRIP_TO_POMMEL) * 0.5f;
		poseStack.pushPose();
		poseStack.translate(grip.x + direction.x * center, grip.y + direction.y * center, grip.z + direction.z * center);
		poseStack.rotate(new Quaternionf().rotationTo(0, 1, 0, direction.x, direction.y, direction.z));
		poseStack.rotate(new Quaternionf().rotationY(roll));
		float scale = SaberGeometry.HILT_LENGTH;
		poseStack.scale(scale, scale, scale);
		hilt.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
		poseStack.popPose();
	}

	// ------------------------------------------------------------------------------------------

	/** Prisma mit spitz zulaufendem Ende, das die Klinge als leuchtende "Kapsel" darstellt. */
	private static void emitCapsule(PoseStack.Pose pose, VertexConsumer consumer, Vector3f base, Vector3f axis, float length,
									float radius, Vector3f u, Vector3f v, float r, float g, float b, float a) {
		// Hinten etwas über den Emitter hinaus, vorne zur Spitze verjüngt.
		float start = -radius * 0.4f;
		float bodyEnd = Math.max(start, length - radius * 1.5f);
		float tipEnd = length + radius * 0.6f;

		Vector3f center0 = new Vector3f(axis).mul(start).add(base);
		Vector3f center1 = new Vector3f(axis).mul(bodyEnd).add(base);
		Vector3f tipPoint = new Vector3f(axis).mul(tipEnd).add(base);

		Vector3f[] ring0 = ring(center0, u, v, radius);
		Vector3f[] ring1 = ring(center1, u, v, radius);

		for (int i = 0; i < SIDES; i++) {
			int j = (i + 1) % SIDES;
			outwardQuad(pose, consumer, ring0[i], ring0[j], ring1[j], ring1[i], center0, center1, r, g, b, a);
			// Spitze: Dreieck als entarteter Quad
			outwardQuad(pose, consumer, ring1[i], ring1[j], tipPoint, tipPoint, center1, tipPoint, r, g, b, a);
		}
	}

	private static Vector3f[] ring(Vector3f center, Vector3f u, Vector3f v, float radius) {
		Vector3f[] ring = new Vector3f[SIDES];
		for (int i = 0; i < SIDES; i++) {
			float angle = Mth.TWO_PI * i / SIDES;
			float cos = Mth.cos(angle) * radius;
			float sin = Mth.sin(angle) * radius;
			ring[i] = new Vector3f(center).add(u.x * cos + v.x * sin, u.y * cos + v.y * sin, u.z * cos + v.z * sin);
		}
		return ring;
	}

	/** Gibt einen Quad so aus, dass seine Vorderseite von der Achse weg zeigt (wichtig für Backface-Culling). */
	private static void outwardQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
									Vector3f axisA, Vector3f axisB, float r, float g, float b, float a) {
		Vector3f e1 = new Vector3f(p1).sub(p0);
		Vector3f e2 = new Vector3f(p3).sub(p0);
		Vector3f normal = e1.cross(e2);

		Vector3f mid = new Vector3f(p0).add(p1).add(p2).add(p3).mul(0.25f);
		Vector3f axisMid = new Vector3f(axisA).add(axisB).mul(0.5f);
		Vector3f outward = mid.sub(axisMid);

		if (normal.dot(outward) >= 0) {
			vertex(pose, consumer, p0, r, g, b, a);
			vertex(pose, consumer, p1, r, g, b, a);
			vertex(pose, consumer, p2, r, g, b, a);
			vertex(pose, consumer, p3, r, g, b, a);
		} else {
			vertex(pose, consumer, p3, r, g, b, a);
			vertex(pose, consumer, p2, r, g, b, a);
			vertex(pose, consumer, p1, r, g, b, a);
			vertex(pose, consumer, p0, r, g, b, a);
		}
	}

	private static void doubleSidedQuad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
										float r, float g, float b, float a0, float a1) {
		vertex(pose, consumer, p0, r, g, b, a0);
		vertex(pose, consumer, p1, r, g, b, a0);
		vertex(pose, consumer, p2, r, g, b, a1);
		vertex(pose, consumer, p3, r, g, b, a1);

		vertex(pose, consumer, p3, r, g, b, a1);
		vertex(pose, consumer, p2, r, g, b, a1);
		vertex(pose, consumer, p1, r, g, b, a0);
		vertex(pose, consumer, p0, r, g, b, a0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vector3f p, float r, float g, float b, float a) {
		consumer.addVertex(pose, p.x, p.y, p.z).setColor(r, g, b, a);
	}

	private static void perpendicularBasis(Vector3f axis, Vector3f u, Vector3f v) {
		Vector3f helper = Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		axis.cross(helper, u).normalize();
		axis.cross(u, v).normalize();
	}
}
