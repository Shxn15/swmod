package com.shxn15.swmod.force;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Bewegt Wesen per Macht, so dass alle Clients es mitbekommen. */
public final class ForceMotion {
	private ForceMotion() {
	}

	/**
	 * Setzt die Geschwindigkeit eines Wesens. Spieler steuern ihre Bewegung selbst, deshalb muss
	 * ihnen die neue Geschwindigkeit ausdrücklich geschickt werden.
	 */
	public static void setVelocity(Entity target, Vec3 velocity) {
		target.setDeltaMovement(velocity);
		target.needsSync = true;
		if (target instanceof Player) {
			target.syncVelocity = true;
		}
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
		}
	}

	/**
	 * Wie leicht lässt sich ein Wesen bewegen (1 = voll, kleiner = schwerer)? Berücksichtigt
	 * Rückstoßresistenz und Größe: einen Eisengolem schiebt man nicht so leicht wie einen Zombie.
	 */
	public static double mobility(Entity target) {
		double factor = 1.0;
		if (target instanceof LivingEntity living) {
			factor *= 1.0 - 0.6 * Mth.clamp(living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0, 1.0);
		}
		double volume = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
		factor *= Mth.clamp(1.4 / (0.6 + volume * 0.5), 0.3, 1.0);
		return factor;
	}

	/**
	 * Anfangsgeschwindigkeit, mit der ein Wesen ungefähr {@code distance} Blöcke weit gleitet,
	 * bevor die Luftreibung es abbremst (für einen Machtzug, der vor den Füßen landet).
	 */
	public static double speedForDistance(Entity target, double distance) {
		double friction = target instanceof LivingEntity ? 0.91 : 0.98;
		return Math.max(0, distance) * (1.0 - friction);
	}
}
