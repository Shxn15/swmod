package com.shxn15.swmod.client.force.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.client.render.SaberRenderTypes;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceTuning;

/**
 * Zeichnet Machtblitze: aus der Hand des Wirkenden zucken gezackte, verästelte Blitze zu jedem
 * Ziel (bzw. auf die Klinge eines Lichtschwerts in Deckung) und springen bei Kettenblitzen von
 * Ziel zu Ziel. Ohne Ziel fahren sie knisternd in die Luft.
 *
 * <p>Die Form entsteht durch rekursives Verschieben von Mittelpunkten und wird viele Male pro
 * Sekunde neu ausgewürfelt; jeder Blitz besteht aus einem weißen Kern und zwei violetten
 * Leuchtbändern, die additiv aufeinanderliegen.
 */
public final class LightningRenderer {
	/** Leuchtbänder: halbe Breite, Rot, Grün, Blau, Deckkraft (außen nach innen). */
	private static final float[][] LAYERS = {
			{0.16f, 0.45f, 0.25f, 1.0f, 0.10f},
			{0.06f, 0.70f, 0.60f, 1.0f, 0.35f},
			{0.018f, 1.0f, 1.0f, 1.0f, 0.95f},
	};
	/** Wie oft pro Tick die Blitze neu zucken. */
	private static final float FLICKER_RATE = 1.6f;

	private LightningRenderer() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(LightningRenderer::render);
	}

	private static void render(LevelRenderContext context) {
		Int2ObjectMap<ForceFx.Channel> channels = ForceFx.channels();
		if (channels.isEmpty()) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float time = ForceClientState.ticks() + partialTick;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		List<List<Vec3>> bolts = new ArrayList<>();
		List<Float> widths = new ArrayList<>();

		for (Int2ObjectMap.Entry<ForceFx.Channel> entry : channels.int2ObjectEntrySet()) {
			ForceFx.Channel channel = entry.getValue();
			if (channel.ability != ForceAbilities.LIGHTNING) continue;
			Entity caster = level.getEntity(entry.getIntKey());
			if (caster == null) continue;

			Vec3 hand = ForceFx.handPos(caster, partialTick);
			long seed = entry.getIntKey() * 7919L + (long) (time * FLICKER_RATE);
			RandomSource random = RandomSource.create(seed);
			IntList blocked = channel.blocked();

			if (channel.targets.isEmpty()) {
				// Ins Leere: drei Blitze fächern sich im Blickkegel auf.
				float range = ForceTuning.at(ForceTuning.LIGHTNING_RANGE, channel.level);
				Vec3 look = caster.getViewVector(partialTick);
				for (int i = 0; i < 3; i++) {
					Vec3 dir = look.add(jitter(random, 0.35)).normalize();
					Vec3 end = hand.add(dir.scale(range * (0.55 + random.nextDouble() * 0.45)));
					HitResult hit = level.clip(new ClipContext(hand, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
					if (hit.getType() != HitResult.Type.MISS) {
						end = hit.getLocation();
					}
					addBolt(bolts, widths, hand, end, random, 0.8f, true);
				}
			} else {
				for (int id : channel.targets) {
					Entity target = level.getEntity(id);
					if (target == null) continue;
					Vec3 end = blocked.contains(id)
							? saberPoint(target, partialTick)
							: target.getBoundingBox().getCenter().add(jitter(random, target.getBbWidth() * 0.3)).add(0, (random.nextDouble() - 0.5) * target.getBbHeight() * 0.5, 0);
					addBolt(bolts, widths, hand, end, random, 1.0f, true);
					addBolt(bolts, widths, hand, end.add(jitter(random, 0.15)), random, 0.55f, false);
					if (!blocked.contains(id)) {
						addCrawl(bolts, widths, target, partialTick, random);
					}
				}
				IntList chains = channel.chains();
				for (int i = 0; i + 1 < chains.size(); i += 2) {
					Entity from = level.getEntity(chains.getInt(i));
					Entity to = level.getEntity(chains.getInt(i + 1));
					if (from == null || to == null) continue;
					addBolt(bolts, widths, center(from, partialTick), center(to, partialTick), random, 0.75f, true);
					addCrawl(bolts, widths, to, partialTick, random);
				}
			}
		}

		if (bolts.isEmpty()) {
			return;
		}

		// Leichtes Flackern der Helligkeit, ab und zu ein greller Blitz.
		RandomSource flicker = RandomSource.create((long) (time * FLICKER_RATE) * 31L);
		float intensity = flicker.nextFloat() < 0.12f ? 1.35f : 0.75f + flicker.nextFloat() * 0.25f;

		// Punkte relativ zur Kamera, damit auch weit draußen in der Welt nichts zittert.
		PoseStack poseStack = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(poseStack, SaberRenderTypes.GLOW, (pose, consumer) -> {
			for (int b = 0; b < bolts.size(); b++) {
				List<Vec3> points = bolts.get(b);
				float width = widths.get(b);
				for (float[] layer : LAYERS) {
					emitPolyline(pose, consumer, points, camera, layer[0] * width * (layer == LAYERS[2] ? 1f : intensity),
							layer[1], layer[2], layer[3], Math.min(1f, layer[4] * intensity));
				}
			}
		});
	}

	/** Wo die Klinge eines blockenden Spielers die Blitze abfängt. */
	public static Vec3 saberPoint(Entity defender, float partialTick) {
		Vec3 eye = defender.getEyePosition(partialTick);
		return eye.add(defender.getViewVector(partialTick).scale(0.7)).add(0, -0.15, 0);
	}

	private static Vec3 center(Entity entity, float partialTick) {
		return entity.getPosition(partialTick).add(0, entity.getBbHeight() * 0.5, 0);
	}

	/**
	 * Ein Blitz mit Verästelungen.
	 *
	 * @param scale    Breite relativ zum Hauptblitz
	 * @param branches mit Seitenästen
	 */
	private static void addBolt(List<List<Vec3>> bolts, List<Float> widths, Vec3 from, Vec3 to, RandomSource random, float scale, boolean branches) {
		double length = from.distanceTo(to);
		if (length < 0.05) {
			return;
		}
		List<Vec3> path = new ArrayList<>();
		path.add(from);
		subdivide(path, from, to, length * 0.18, Mth.clamp((int) (Math.log(length * 6) / Math.log(2)) + 1, 3, 7), random);
		path.add(to);
		bolts.add(path);
		widths.add(scale);

		if (!branches) {
			return;
		}
		int count = 1 + random.nextInt(3) + (int) (length / 4);
		for (int i = 0; i < count; i++) {
			int index = 1 + random.nextInt(Math.max(1, path.size() - 2));
			Vec3 start = path.get(index);
			Vec3 main = to.subtract(from).normalize();
			Vec3 dir = main.add(jitter(random, 0.9)).normalize();
			double branchLength = length * (0.12 + random.nextDouble() * 0.22);
			Vec3 end = start.add(dir.scale(branchLength));
			List<Vec3> branch = new ArrayList<>();
			branch.add(start);
			subdivide(branch, start, end, branchLength * 0.22, 3, random);
			branch.add(end);
			bolts.add(branch);
			widths.add(scale * 0.45f);
		}
	}

	/** Kleine Blitze, die über den Körper des Getroffenen kriechen. */
	private static void addCrawl(List<List<Vec3>> bolts, List<Float> widths, Entity target, float partialTick, RandomSource random) {
		Vec3 base = target.getPosition(partialTick);
		float w = target.getBbWidth();
		float h = target.getBbHeight();
		for (int i = 0; i < 2; i++) {
			Vec3 a = base.add((random.nextDouble() - 0.5) * w * 1.1, random.nextDouble() * h, (random.nextDouble() - 0.5) * w * 1.1);
			Vec3 b = base.add((random.nextDouble() - 0.5) * w * 1.1, random.nextDouble() * h, (random.nextDouble() - 0.5) * w * 1.1);
			List<Vec3> path = new ArrayList<>();
			path.add(a);
			subdivide(path, a, b, a.distanceTo(b) * 0.3, 3, random);
			path.add(b);
			bolts.add(path);
			widths.add(0.35f);
		}
	}

	/** Mittelpunktverschiebung: fügt die Punkte zwischen a und b (ohne a und b) in die Liste ein. */
	private static void subdivide(List<Vec3> out, Vec3 a, Vec3 b, double displacement, int depth, RandomSource random) {
		if (depth <= 0) {
			return;
		}
		Vec3 axis = b.subtract(a);
		Vec3 offset = jitter(random, 1.0);
		double length = axis.length();
		if (length > 1.0E-6) {
			Vec3 dir = axis.scale(1 / length);
			offset = offset.subtract(dir.scale(offset.dot(dir)));
		}
		Vec3 mid = a.add(b).scale(0.5).add(offset.scale(displacement));
		subdivide(out, a, mid, displacement * 0.55, depth - 1, random);
		out.add(mid);
		subdivide(out, mid, b, displacement * 0.55, depth - 1, random);
	}

	private static Vec3 jitter(RandomSource random, double amount) {
		return new Vec3((random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount, (random.nextDouble() - 0.5) * 2 * amount);
	}

	/** Ein Leuchtband entlang der Punkte, immer zur Kamera gedreht, in der Mitte hell und zu den Rändern durchsichtig. */
	private static void emitPolyline(PoseStack.Pose pose, VertexConsumer consumer, List<Vec3> points, Vec3 camera, float halfWidth,
									 float r, float g, float b, float alpha) {
		for (int i = 0; i + 1 < points.size(); i++) {
			Vec3 p0 = points.get(i);
			Vec3 p1 = points.get(i + 1);
			Vec3 side = side(points, i, camera).scale(halfWidth);
			Vec3 nextSide = side(points, i + 1, camera).scale(halfWidth);

			// Linke und rechte Hälfte: Mitte deckend, Rand durchsichtig.
			vertex(pose, consumer, p0.subtract(side), camera, r, g, b, 0f);
			vertex(pose, consumer, p0, camera, r, g, b, alpha);
			vertex(pose, consumer, p1, camera, r, g, b, alpha);
			vertex(pose, consumer, p1.subtract(nextSide), camera, r, g, b, 0f);

			vertex(pose, consumer, p0, camera, r, g, b, alpha);
			vertex(pose, consumer, p0.add(side), camera, r, g, b, 0f);
			vertex(pose, consumer, p1.add(nextSide), camera, r, g, b, 0f);
			vertex(pose, consumer, p1, camera, r, g, b, alpha);
		}
	}

	/** Seitenrichtung an einem Punkt: senkrecht zum Blitz (gemittelt über die Nachbarn) und zur Blickrichtung. */
	private static Vec3 side(List<Vec3> points, int index, Vec3 camera) {
		Vec3 prev = points.get(Math.max(0, index - 1));
		Vec3 next = points.get(Math.min(points.size() - 1, index + 1));
		Vec3 tangent = next.subtract(prev);
		Vec3 view = points.get(index).subtract(camera);
		Vec3 side = tangent.cross(view);
		double length = side.length();
		return length < 1.0E-6 ? new Vec3(0, 1, 0) : side.scale(1 / length);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, Vec3 p, Vec3 camera, float r, float g, float b, float a) {
		consumer.addVertex(pose, (float) (p.x - camera.x), (float) (p.y - camera.y), (float) (p.z - camera.z)).setColor(r, g, b, a);
	}
}
