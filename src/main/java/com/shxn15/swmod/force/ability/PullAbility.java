package com.shxn15.swmod.force.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceImpacts;
import com.shxn15.swmod.force.ForceMotion;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceTargeting;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtzug: zieht alles im Blickkegel zu sich heran, Gegenstände und Erfahrung direkt in die Hand.
 * Auf Stufe 3 reißt er Gegnern die Waffe aus der Hand.
 */
public class PullAbility extends ForceAbility {
	public PullAbility() {
		super(Activation.INSTANT, ForceSide.LIGHT, 0x9ADFFF, 3);
	}

	@Override
	public int learnCost(int level) {
		return at(PULL_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(PULL_COST, level);
	}

	@Override
	public int cooldown(int level) {
		return at(PULL_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.blocks("range", level -> at(PULL_RANGE, level)),
				ForceStat.degrees("width", level -> at(PULL_ANGLE, level) * 2),
				ForceStat.percent("strength", level -> at(PULL_STRENGTH, level) / at(PULL_STRENGTH, 1)),
				ForceStat.flag("disarm", level -> level >= PULL_DISARM_LEVEL),
				ForceStat.number("cost", level -> at(PULL_COST, level)));
	}

	@Override
	public boolean cast(ForceContext context) {
		ServerPlayer player = context.player();
		ServerLevel level = context.world();
		int lvl = context.level();
		Vec3 chest = player.position().add(0, player.getBbHeight() * 0.6, 0);

		List<Integer> hit = new ArrayList<>();
		List<Integer> disarmed = new ArrayList<>();
		for (Entity target : ForceTargeting.cone(player, at(PULL_RANGE, lvl), at(PULL_ANGLE, lvl),
				entity -> ForceTargeting.isMovable(entity) && !ForceTargeting.isFriendly(player, entity))) {
			hit.add(target.getId());

			if (target instanceof ItemEntity || target instanceof ExperienceOrb) {
				pullTo(target, chest, 0.6, 1.6);
				continue;
			}
			if (target instanceof Projectile projectile && projectile.getOwner() != player) {
				ForceMotion.setVelocity(projectile, projectile.getDeltaMovement().scale(0.1));
				continue;
			}

			if (lvl >= PULL_DISARM_LEVEL && target instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
				ItemStack weapon = mob.getMainHandItem().copy();
				mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				ItemEntity drop = mob.spawnAtLocation(level, weapon, mob.getBbHeight() * 0.6f);
				if (drop != null) {
					drop.setPickUpDelay(8);
					pullTo(drop, chest, 0.6, 1.6);
					disarmed.add(drop.getId());
				}
			}

			double strength = at(PULL_STRENGTH, lvl) * ForceMotion.mobility(target);
			pullTo(target, chest, 1.8 + target.getBbWidth(), strength);
			if (target instanceof LivingEntity) {
				ForceImpacts.track(target, player, 0.8f, context.now());
			}
		}

		context.fx(ForceFxPayload.CAST, hit, disarmed);
		context.playSound(ModSounds.FORCE_PULL, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		return true;
	}

	/** Zieht das Wesen so heran, dass es ungefähr {@code stopDistance} vor dem Ziel landet. */
	private static void pullTo(Entity target, Vec3 destination, double stopDistance, double maxSpeed) {
		Vec3 to = destination.subtract(ForceTargeting.center(target));
		double distance = to.horizontalDistance();
		Vec3 flat = distance > 1.0E-3 ? new Vec3(to.x / distance, 0, to.z / distance) : Vec3.ZERO;
		double speed = Math.min(maxSpeed, ForceMotion.speedForDistance(target, distance - stopDistance) + 0.12);
		double lift = 0.28 + Math.min(0.35, distance * 0.025) + Math.max(0, to.y) * 0.12;
		ForceMotion.setVelocity(target, new Vec3(flat.x * speed, lift, flat.z * speed));
	}
}
