package com.shxn15.swmod.force.ability;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceImpacts;
import com.shxn15.swmod.force.ForceMotion;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceTargeting;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtwürgen: Rechtsklick halten packt das anvisierte Wesen an der Kehle, hebt es hoch und hält
 * es dort fest, während es ständig Leben verliert. Auf Stufe 3 wird es beim Loslassen in
 * Blickrichtung geschleudert.
 */
public class GripAbility extends ForceAbility {
	public GripAbility() {
		super(Activation.CHANNEL, ForceSide.DARK, 0xFF3B3B, 3);
	}

	static final class Data {
		final int target;
		final Vec3 anchor;
		int ticks;

		Data(int target, Vec3 anchor) {
			this.target = target;
			this.anchor = anchor;
		}
	}

	@Override
	public int learnCost(int level) {
		return at(GRIP_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(GRIP_COST, level);
	}

	@Override
	public float drain(int level) {
		return at(GRIP_DRAIN, level);
	}

	@Override
	public int cooldown(int level) {
		return at(GRIP_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.blocks("range", level -> at(GRIP_RANGE, level)),
				ForceStat.hearts("dps", level -> at(GRIP_DAMAGE, level) * 20f / GRIP_HIT_INTERVAL),
				ForceStat.blocks("max_size", level -> at(GRIP_MAX_HEIGHT, level)),
				ForceStat.flag("throw", level -> level >= GRIP_THROW_LEVEL),
				ForceStat.number("drain", level -> at(GRIP_DRAIN, level) * 20));
	}

	@Override
	public @Nullable Object startChannel(ForceContext context) {
		ServerPlayer player = context.player();
		int lvl = context.level();
		Entity target = ForceTargeting.aimed(player, at(GRIP_RANGE, lvl),
				entity -> ForceTargeting.isLivingTarget(entity) && !ForceTargeting.isFriendly(player, entity));
		if (target == null) {
			context.event(ForceEventPayload.NO_TARGET);
			return null;
		}
		if (!ForceTargeting.fits(target, at(GRIP_MAX_WIDTH, lvl), at(GRIP_MAX_HEIGHT, lvl))) {
			context.event(ForceEventPayload.TOO_HEAVY);
			return null;
		}

		target.stopRiding();
		context.fx(ForceFxPayload.CHANNEL_START, List.of(target.getId()));
		context.playSound(ModSounds.FORCE_GRIP, 1.0f, 1.0f);
		return new Data(target.getId(), liftPoint(context.world(), target, at(GRIP_LIFT, lvl)));
	}

	/** Etwas über dem Ziel, aber nicht in die Decke. */
	private static Vec3 liftPoint(ServerLevel level, Entity target, float lift) {
		Vec3 feet = target.position();
		Vec3 head = feet.add(0, target.getBbHeight(), 0);
		HitResult ceiling = level.clip(new ClipContext(head, head.add(0, lift + 0.2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
		double room = ceiling.getType() == HitResult.Type.MISS ? lift : Math.max(0, ceiling.getLocation().y - head.y - 0.2);
		return feet.add(0, Math.min(lift, room), 0);
	}

	@Override
	public boolean tickChannel(ForceContext context, Object raw) {
		Data data = (Data) raw;
		ServerLevel level = context.world();
		if (!(level.getEntity(data.target) instanceof LivingEntity target) || !target.isAlive()) {
			return false;
		}
		if (ForceTargeting.center(target).distanceTo(context.eye()) > at(GRIP_RANGE, context.level()) + 4) {
			return false;
		}

		data.ticks++;
		// Festhalten mit leichtem Zappeln.
		RandomSource random = level.getRandom();
		Vec3 shake = new Vec3((random.nextFloat() - 0.5f) * 0.05, (random.nextFloat() - 0.5f) * 0.03, (random.nextFloat() - 0.5f) * 0.05);
		Vec3 velocity = data.anchor.add(shake).subtract(target.position()).scale(0.3);
		if (velocity.length() > 0.6) {
			velocity = velocity.normalize().scale(0.6);
		}
		ForceMotion.setVelocity(target, velocity);
		target.resetFallDistance();
		if (target instanceof Mob mob) {
			mob.setTarget(null);
		}

		if (data.ticks % GRIP_HIT_INTERVAL == 0) {
			ModDamageTypes.skipHurtCooldown(target);
			target.hurtServer(level, ModDamageTypes.forceGrip(level, context.player()), at(GRIP_DAMAGE, context.level()));
		}
		return target.isAlive();
	}

	@Override
	public void stopChannel(ForceContext context, Object raw) {
		Data data = (Data) raw;
		Entity target = context.world().getEntity(data.target);
		if (target != null && target.isAlive() && context.level() >= GRIP_THROW_LEVEL) {
			ForceMotion.setVelocity(target, context.look().scale(GRIP_THROW_SPEED * ForceMotion.mobility(target)).add(0, 0.25, 0));
			ForceImpacts.track(target, context.player(), 1.2f, context.now());
			context.playSound(ModSounds.FORCE_THROW, 0.9f, 0.8f);
		}
		context.fx(ForceFxPayload.CHANNEL_END, List.of(data.target));
	}
}
