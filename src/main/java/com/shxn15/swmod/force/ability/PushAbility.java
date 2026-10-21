package com.shxn15.swmod.force.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceImpacts;
import com.shxn15.swmod.force.ForceMotion;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceTargeting;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtstoß: eine Druckwelle im Blickkegel schleudert alles weg, was sich bewegen lässt.
 * Geschosse fliegen dorthin zurück, wohin man schaut. Wer gegen eine Wand kracht, nimmt Schaden.
 */
public class PushAbility extends ForceAbility {
	public PushAbility() {
		super(Activation.INSTANT, ForceSide.LIGHT, 0x7CC8FF, 3);
	}

	@Override
	public int learnCost(int level) {
		return at(PUSH_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(PUSH_COST, level);
	}

	@Override
	public int cooldown(int level) {
		return at(PUSH_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.blocks("range", level -> at(PUSH_RANGE, level)),
				ForceStat.degrees("width", level -> at(PUSH_ANGLE, level) * 2),
				ForceStat.percent("strength", level -> at(PUSH_STRENGTH, level) / at(PUSH_STRENGTH, 1)),
				ForceStat.hearts("damage", level -> at(PUSH_DAMAGE, level)),
				ForceStat.number("cost", level -> at(PUSH_COST, level)));
	}

	@Override
	public boolean cast(ForceContext context) {
		ServerPlayer player = context.player();
		ServerLevel level = context.world();
		int lvl = context.level();
		float range = at(PUSH_RANGE, lvl);
		Vec3 eye = context.eye();
		Vec3 look = context.look();

		List<Integer> hit = new ArrayList<>();
		for (Entity target : ForceTargeting.cone(player, range, at(PUSH_ANGLE, lvl),
				entity -> ForceTargeting.isMovable(entity) && !ForceTargeting.isFriendly(player, entity))) {
			hit.add(target.getId());

			if (target instanceof Projectile projectile) {
				// Zurück zum Absender – oder wohin auch immer man gerade schaut.
				double speed = Math.max(1.3, projectile.getDeltaMovement().length() * 1.2);
				projectile.setOwner(player);
				ForceMotion.setVelocity(projectile, look.scale(speed));
				continue;
			}

			Vec3 away = ForceTargeting.center(target).subtract(eye);
			double distance = away.length();
			Vec3 direction = distance < 1.0E-3 ? look : look.scale(0.55).add(away.scale(0.45 / distance)).normalize();
			double falloff = 1.0 - 0.45 * Math.min(1.0, distance / range);
			double power = at(PUSH_STRENGTH, lvl) * falloff * ForceMotion.mobility(target);
			double lift = at(PUSH_LIFT, lvl) * falloff * Math.min(1.0, ForceMotion.mobility(target) * 1.5);

			if (target instanceof LivingEntity living) {
				living.hurtServer(level, ModDamageTypes.force(level, player), at(PUSH_DAMAGE, lvl));
				ForceImpacts.track(target, player, 1.0f + 0.35f * (lvl - 1), context.now());
			}
			Vec3 velocity = new Vec3(direction.x * power, Math.max(0, direction.y) * power + lift, direction.z * power);
			ForceMotion.setVelocity(target, target.getDeltaMovement().scale(0.2).add(velocity));
		}

		context.fx(ForceFxPayload.CAST, hit);
		context.playSound(ModSounds.FORCE_PUSH, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f);
		return true;
	}
}
