package com.shxn15.swmod.force;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;

/** Zielsuche für Machtfähigkeiten: Blickkegel, Zielhilfe und Sichtlinie. */
public final class ForceTargeting {
	private ForceTargeting() {
	}

	public static Vec3 center(Entity entity) {
		return entity.getBoundingBox().getCenter();
	}

	/** Kann die Macht dieses Wesen überhaupt bewegen? (Keine Gemälde, Marker, Bosse, ...) */
	public static boolean isMovable(Entity entity) {
		return entity.isAlive() && !entity.isSpectator()
				&& !(entity instanceof HangingEntity) && !(entity instanceof Marker) && !(entity instanceof Display)
				&& !(entity instanceof AreaEffectCloud) && !(entity instanceof EndCrystal)
				&& !(entity instanceof EnderDragon) && !(entity instanceof EnderDragonPart) && !(entity instanceof WitherBoss)
				&& !(entity instanceof ThrownLightsaberEntity);
	}

	/** Lebewesen, die man würgen oder mit Blitzen treffen kann. */
	public static boolean isLivingTarget(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand);
	}

	/** Eigene Haustiere (und der Spieler selbst) werden von Angriffen verschont. */
	public static boolean isFriendly(ServerPlayer player, Entity entity) {
		if (entity == player) {
			return true;
		}
		return entity instanceof OwnableEntity ownable && ownable.getOwner() == player;
	}

	/** Gibt es freie Sicht vom Auge des Spielers auf das Wesen? */
	public static boolean canSee(ServerPlayer player, Entity target) {
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		Vec3[] points = {box.getCenter(), target.getEyePosition(), new Vec3(box.getCenter().x, box.minY + 0.1, box.getCenter().z)};
		for (Vec3 point : points) {
			if (player.level().clip(new ClipContext(eye, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
					== HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Alle Wesen im Blickkegel des Spielers, die man sehen kann, nach Abstand sortiert.
	 *
	 * @param halfAngle halber Öffnungswinkel in Grad
	 */
	public static List<Entity> cone(ServerPlayer player, double range, double halfAngle, Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		double cosLimit = Math.cos(Math.toRadians(halfAngle));
		List<Entity> result = new ArrayList<>();

		for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(range + 1.0), filter)) {
			if (entity.isPassengerOfSameVehicle(player) || entity.hasPassenger(player)) continue;
			if (inCone(eye, look, entity.getBoundingBox(), range, cosLimit) && canSee(player, entity)) {
				result.add(entity);
			}
		}

		result.sort(Comparator.comparingDouble(entity -> center(entity).distanceToSqr(eye)));
		return result;
	}

	/**
	 * Das Wesen, auf das der Spieler zielt. Erst exakt entlang des Fadenkreuzes, dann mit etwas
	 * Zielhilfe (kleinster Winkel), damit man auch bewegliche Ziele gut erwischt.
	 */
	public static @Nullable Entity aimed(ServerPlayer player, double range, Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double maxDistance = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);

		Entity best = null;
		double bestDistance = maxDistance;
		AABB sweep = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
		for (Entity entity : player.level().getEntities(player, sweep, filter)) {
			AABB box = entity.getBoundingBox().inflate(Math.max(0.25, entity.getPickRadius()));
			double distance;
			if (box.contains(eye)) {
				distance = 0;
			} else {
				var hit = box.clip(eye, end);
				if (hit.isEmpty()) continue;
				distance = hit.get().distanceTo(eye);
			}
			if (distance <= bestDistance) {
				best = entity;
				bestDistance = distance;
			}
		}
		if (best != null) {
			return best;
		}

		// Zielhilfe: das Wesen mit dem kleinsten Winkel zum Fadenkreuz.
		double bestAngle = Math.cos(Math.toRadians(ForceTuning.AIM_ASSIST_ANGLE));
		for (Entity entity : cone(player, range, ForceTuning.AIM_ASSIST_ANGLE, filter)) {
			Vec3 to = center(entity).subtract(eye).normalize();
			double cos = to.dot(look);
			if (cos >= bestAngle) {
				best = entity;
				bestAngle = cos;
			}
		}
		return best;
	}

	/** Liegt die Box (ganz oder teilweise) im Kegel? */
	static boolean inCone(Vec3 eye, Vec3 look, AABB box, double range, double cosLimit) {
		if (box.contains(eye) || box.clip(eye, eye.add(look.scale(range))).isPresent()) {
			return true;
		}
		// Der Punkt der Box, der dem Blickstrahl am nächsten liegt.
		Vec3 center = box.getCenter();
		double along = Math.max(0, center.subtract(eye).dot(look));
		Vec3 onRay = eye.add(look.scale(along));
		Vec3 nearest = new Vec3(
				Mth.clamp(onRay.x, box.minX, box.maxX),
				Mth.clamp(onRay.y, box.minY, box.maxY),
				Mth.clamp(onRay.z, box.minZ, box.maxZ));
		Vec3 to = nearest.subtract(eye);
		double length = to.length();
		if (length < 1.0E-4) {
			return true;
		}
		return length <= range && to.dot(look) / length >= cosLimit;
	}

	/** Passt das Wesen von der Größe her zur Stufe? */
	public static boolean fits(Entity entity, float maxWidth, float maxHeight) {
		return entity.getBbWidth() <= maxWidth + 1.0E-3f && entity.getBbHeight() <= maxHeight + 1.0E-3f;
	}
}
