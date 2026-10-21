package com.shxn15.swmod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

import com.shxn15.swmod.combat.SaberGeometry;

/**
 * Zeichnet Griff, leuchtende Klinge und Bewegungsschweif. Alle Koordinaten sind im lokalen Raum
 * des übergebenen PoseStacks.
 *
 * <p>Die Klinge besteht aus mehreren Leuchtbändern, die immer zur Kamera zeigen. Jedes Band ist
 * in der Mitte hell und läuft zum Rand weich auf null aus; additiv übereinandergelegt ergibt das
 * einen fast weißen Kern mit farbigem, weichem Glühen, wie in den Filmen.
 */
public final class SaberRenderer {
	/** Halbe Breite, Weißanteil, Deckkraft Mitte, Deckkraft Rand je Leuchtband (innen nach außen). */
	private static final float[][] LAYERS = {
			{0.010f, 1.00f, 1.00f, 0.80f},
			{0.024f, 0.55f, 0.85f, 0.00f},
			{0.060f, 0.12f, 0.55f, 0.00f},
			{0.150f, 0.00f, 0.20f, 0.00f},
	};

	private SaberRenderer() {
	}

	/**
	 * Klinge vom Emitter {@code base} bis zur Spitze {@code tip}.
	 *
	 * @param intensity 0..1, z.B. für Bewegungsunschärfe
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

		// Leichtes, unregelmäßiges Flackern wie bei einer Plasmaklinge (nur das äußere Glühen).
		float flicker = 0.92f + 0.05f * Mth.sin(time * 2.7f) + 0.03f * Mth.sin(time * 7.3f + 1.3f);

		collector.submitCustomGeometry(poseStack, SaberRenderTypes.GLOW, (pose, consumer) -> {
			// Die Kamera sitzt im Ursprung des Zielraums; zurückgerechnet in den lokalen Raum.
			Vector3f camera = new Matrix4f(pose.pose()).invert().transformPosition(new Vector3f());
			Vector3f side = sideVector(camera, base, tip, axis);

			// Aus der Ferne wächst das Glühen mit, damit die Klinge sichtbar bleibt.
			float distance = new Vector3f(base).add(tip).mul(0.5f).distance(camera);
			float distanceScale = Mth.clamp(distance / 2.2f, 1f, 3.5f);

			for (int i = 0; i < LAYERS.length; i++) {
				float[] layer = LAYERS[i];
				boolean outer = i >= 2;
				float grow = i == 0 ? 1f : (outer ? distanceScale : (float) Math.sqrt(distanceScale));
				float halfWidth = layer[0] * grow * (outer ? (0.96f + 0.04f * flicker) : 1f);
				float white = layer[1];
				float alphaCenter = layer[2] * intensity * (outer ? flicker : 1f);
				float alphaEdge = layer[3] * intensity;
				float lr = Mth.lerp(white, r, 1f);
				float lg = Mth.lerp(white, g, 1f);
				float lb = Mth.lerp(white, b, 1f);
				emitRibbon(pose, consumer, base, tip, axis, side, halfWidth, lr, lg, lb, alphaCenter, alphaEdge, !outer);
			}
		});
	}

	/**
	 * Schweif hinter einer schnell bewegten Klinge: ein Band zwischen den vergangenen Positionen
	 * von Emitter und Spitze. Zur Spitze hin heller, nach hinten (älter) ausblendend.
	 */
	public static void submitTrail(PoseStack poseStack, SubmitNodeCollector collector, Vector3f[] bases, Vector3f[] tips,
								   float[] alphas, int rgb) {
		if (bases.length < 2) {
			return;
		}

		float r = ((rgb >> 16) & 0xFF) / 255f;
		float g = ((rgb >> 8) & 0xFF) / 255f;
		float b = (rgb & 0xFF) / 255f;
		float wr = Mth.lerp(0.5f, r, 1f);
		float wg = Mth.lerp(0.5f, g, 1f);
		float wb = Mth.lerp(0.5f, b, 1f);

		collector.submitCustomGeometry(poseStack, SaberRenderTypes.GLOW, (pose, consumer) -> {
			for (int i = 0; i < bases.length - 1; i++) {
				float a0 = alphas[i];
				float a1 = alphas[i + 1];
				if (a0 <= 0.003f && a1 <= 0.003f) continue;

				// Farbiges Band über die ganze Klinge, innen am Emitter unsichtbar.
				vertex(pose, consumer, bases[i], r, g, b, 0f);
				vertex(pose, consumer, tips[i], r, g, b, a0);
				vertex(pose, consumer, tips[i + 1], r, g, b, a1);
				vertex(pose, consumer, bases[i + 1], r, g, b, 0f);

				// Hellerer Streifen im äußeren Drittel, wo sich die Klinge am schnellsten bewegt.
				Vector3f m0 = new Vector3f(bases[i]).lerp(tips[i], 0.6f);
				Vector3f m1 = new Vector3f(bases[i + 1]).lerp(tips[i + 1], 0.6f);
				vertex(pose, consumer, m0, wr, wg, wb, 0f);
				vertex(pose, consumer, tips[i], wr, wg, wb, a0 * 0.6f);
				vertex(pose, consumer, tips[i + 1], wr, wg, wb, a1 * 0.6f);
				vertex(pose, consumer, m1, wr, wg, wb, 0f);
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

	/** Richtung quer zur Klinge, senkrecht zur Blickrichtung der Kamera. */
	private static Vector3f sideVector(Vector3f camera, Vector3f base, Vector3f tip, Vector3f axis) {
		Vector3f middle = new Vector3f(base).add(tip).mul(0.5f);
		Vector3f view = middle.sub(camera);
		Vector3f side = new Vector3f(axis).cross(view);
		if (side.lengthSquared() < 1.0E-8f) {
			// Kamera schaut genau entlang der Klinge: beliebige Senkrechte nehmen.
			side = new Vector3f(axis).cross(Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0));
		}
		return side.normalize();
	}

	/** Ein Leuchtband mit Verlauf (Mitte -> Rand) und abgerundeter Spitze. */
	private static void emitRibbon(PoseStack.Pose pose, VertexConsumer consumer, Vector3f base, Vector3f tip, Vector3f axis,
								   Vector3f side, float halfWidth, float r, float g, float b, float alphaCenter, float alphaEdge,
								   boolean baseCap) {
		Vector3f offset = new Vector3f(side).mul(halfWidth);
		Vector3f l0 = new Vector3f(base).sub(offset);
		Vector3f r0 = new Vector3f(base).add(offset);
		Vector3f l1 = new Vector3f(tip).sub(offset);
		Vector3f r1 = new Vector3f(tip).add(offset);

		// Körper: zwei Hälften, jeweils von der hellen Mitte zum dunklen Rand.
		quad(pose, consumer, l0, base, tip, l1, r, g, b, alphaEdge, alphaCenter, alphaCenter, alphaEdge);
		quad(pose, consumer, base, r0, r1, tip, r, g, b, alphaCenter, alphaEdge, alphaEdge, alphaCenter);

		// Abgerundete Spitze.
		Vector3f cap = new Vector3f(axis).mul(halfWidth * 1.2f).add(tip);
		quad(pose, consumer, l1, tip, cap, cap, r, g, b, alphaEdge, alphaCenter, 0f, 0f);
		quad(pose, consumer, tip, r1, cap, cap, r, g, b, alphaCenter, alphaEdge, 0f, 0f);

		if (baseCap) {
			Vector3f back = new Vector3f(axis).mul(-halfWidth * 0.6f).add(base);
			quad(pose, consumer, base, l0, back, back, r, g, b, alphaCenter, alphaEdge, 0f, 0f);
			quad(pose, consumer, r0, base, back, back, r, g, b, alphaEdge, alphaCenter, 0f, 0f);
		}
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer consumer, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
							 float r, float g, float b, float a0, float a1, float a2, float a3) {
		vertex(pose, consumer, p0, r, g, b, a0);
		vertex(pose, consumer, p1, r, g, b, a1);
		vertex(pose, consumer, p2, r, g, b, a2);
		vertex(pose, consumer, p3, r, g, b, a3);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vector3f p, float r, float g, float b, float a) {
		consumer.addVertex(pose, p.x, p.y, p.z).setColor(r, g, b, a);
	}
}
