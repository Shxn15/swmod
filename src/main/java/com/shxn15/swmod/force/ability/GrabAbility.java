package com.shxn15.swmod.force.ability;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceImpacts;
import com.shxn15.swmod.force.ForceMotion;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceTargeting;
import com.shxn15.swmod.network.ForceControlPayload;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtgriff (Telekinese): Rechtsklick halten hebt das anvisierte Wesen hoch, es schwebt leuchtend
 * vor einem her und folgt dem Blick. Mausrad ändert den Abstand, Linksklick schleudert es weg,
 * Loslassen lässt es mit Schwung fliegen – schnelle Mausbewegungen werden zu Würfen.
 */
public class GrabAbility extends ForceAbility {
	/** Höchste Geschwindigkeit, mit der das Ziel dem Blick folgt (Blöcke/Tick). */
	private static final double MAX_FOLLOW_SPEED = 2.6;

	public GrabAbility() {
		super(Activation.CHANNEL, ForceSide.LIGHT, 0x58B7FF, 3);
	}

	static final class Data {
		final int target;
		double distance;
		Vec3 velocity = Vec3.ZERO;
		int ticks;
		boolean hurled;

		Data(int target, double distance) {
			this.target = target;
			this.distance = distance;
		}
	}

	@Override
	public int learnCost(int level) {
		return at(GRAB_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(GRAB_COST, level);
	}

	@Override
	public float drain(int level) {
		return at(GRAB_DRAIN, level);
	}

	@Override
	public int cooldown(int level) {
		return at(GRAB_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.blocks("range", level -> at(GRAB_RANGE, level)),
				ForceStat.blocks("max_size", level -> at(GRAB_MAX_HEIGHT, level)),
				ForceStat.number("throw", level -> at(GRAB_THROW_SPEED, level)),
				ForceStat.number("drain", level -> at(GRAB_DRAIN, level) * 20));
	}

	@Override
	public @Nullable Object startChannel(ForceContext context) {
		ServerPlayer player = context.player();
		int lvl = context.level();
		float range = at(GRAB_RANGE, lvl);

		Entity target = ForceTargeting.aimed(player, range, entity -> ForceTargeting.isMovable(entity)
				&& !(entity instanceof Projectile) && !entity.hasPassenger(player) && !entity.isPassengerOfSameVehicle(player));
		if (target == null) {
			context.event(ForceEventPayload.NO_TARGET);
			return null;
		}
		if (!ForceTargeting.fits(target, at(GRAB_MAX_WIDTH, lvl), at(GRAB_MAX_HEIGHT, lvl))) {
			context.event(ForceEventPayload.TOO_HEAVY);
			return null;
		}

		target.stopRiding();
		double distance = Mth.clamp(ForceTargeting.center(target).distanceTo(context.eye()), GRAB_MIN_DISTANCE, range);
		context.fx(ForceFxPayload.CHANNEL_START, List.of(target.getId()));
		context.playSound(ModSounds.FORCE_GRAB, 1.0f, 1.0f);
		return new Data(target.getId(), distance);
	}

	@Override
	public boolean tickChannel(ForceContext context, Object raw) {
		Data data = (Data) raw;
		Entity target = context.world().getEntity(data.target);
		if (target == null || !ForceTargeting.isMovable(target)) {
			return false;
		}

		Vec3 center = ForceTargeting.center(target);
		if (center.distanceTo(context.eye()) > at(GRAB_RANGE, context.level()) + 6) {
			return false;
		}

		data.ticks++;
		// Feder mit Dämpfung: folgt dem Blick zügig, schwingt aber kaum über. Dazu ein leichtes
		// Schweben auf und ab wie bei LEGO Star Wars.
		Vec3 anchor = context.eye().add(context.look().scale(data.distance)).add(0, Math.sin(data.ticks * 0.18) * 0.06, 0);
		Vec3 velocity = data.velocity.scale(0.35).add(anchor.subtract(center).scale(0.5));
		if (velocity.length() > MAX_FOLLOW_SPEED) {
			velocity = velocity.normalize().scale(MAX_FOLLOW_SPEED);
		}
		data.velocity = velocity;

		ForceMotion.setVelocity(target, velocity);
		target.resetFallDistance();
		if (target instanceof Mob mob) {
			mob.setTarget(null);
		}
		return true;
	}

	@Override
	public boolean control(ForceContext context, Object raw, int action, float value) {
		Data data = (Data) raw;
		if (action == ForceControlPayload.DISTANCE) {
			data.distance = Mth.clamp(data.distance + value, GRAB_MIN_DISTANCE, at(GRAB_RANGE, context.level()));
			return false;
		}
		if (action == ForceControlPayload.HURL) {
			Entity target = context.world().getEntity(data.target);
			if (target != null) {
				double speed = at(GRAB_THROW_SPEED, context.level()) * Math.max(0.5, ForceMotion.mobility(target));
				data.velocity = context.look().scale(speed).add(0, 0.15, 0);
				data.hurled = true;
				context.playSound(ModSounds.FORCE_THROW, 1.0f, 1.0f);
			}
			return true;
		}
		return false;
	}

	@Override
	public void stopChannel(ForceContext context, Object raw) {
		Data data = (Data) raw;
		Entity target = context.world().getEntity(data.target);
		if (target != null && target.isAlive()) {
			// Schwung behalten: wer die Maus beim Loslassen schnell bewegt, wirft.
			Vec3 velocity = data.velocity;
			double max = at(GRAB_THROW_SPEED, context.level());
			if (velocity.length() > max) {
				velocity = velocity.normalize().scale(max);
			}
			ForceMotion.setVelocity(target, velocity);
			if (target instanceof LivingEntity && (data.hurled || velocity.length() > 0.6)) {
				ForceImpacts.track(target, context.player(), 1.0f + 0.4f * (context.level() - 1), context.now());
			}
		}
		context.fx(ForceFxPayload.CHANNEL_END, List.of(data.target));
	}
}
