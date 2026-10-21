package com.shxn15.swmod.force;

import java.util.List;
import java.util.UUID;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Wer per Macht weggestoßen oder geworfen wurde und gegen eine Wand oder Decke kracht,
 * nimmt Schaden – abhängig von der Aufprallgeschwindigkeit. Ein Treffer pro Stoß.
 */
public final class ForceImpacts {
	private static final Int2ObjectMap<Tracked> TRACKED = new Int2ObjectOpenHashMap<>();

	private ForceImpacts() {
	}

	private static final class Tracked {
		final ServerLevel level;
		final @Nullable UUID attacker;
		final float scale;
		final int until;
		Vec3 lastPos;
		Vec3 lastMotion;

		Tracked(ServerLevel level, @Nullable UUID attacker, float scale, int until, Vec3 pos, Vec3 motion) {
			this.level = level;
			this.attacker = attacker;
			this.scale = scale;
			this.until = until;
			this.lastPos = pos;
			this.lastMotion = motion;
		}
	}

	/**
	 * Beobachtet ein gerade weggeschleudertes Wesen.
	 *
	 * @param scale Faktor für den Aufprallschaden (höhere Stufen = härterer Aufprall)
	 */
	public static void track(Entity entity, @Nullable ServerPlayer attacker, float scale, int now) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		TRACKED.put(entity.getId(), new Tracked(level, attacker != null ? attacker.getUUID() : null, scale,
				now + ForceTuning.IMPACT_TRACK_TICKS, entity.position(), entity.getDeltaMovement()));
	}

	public static void stopTracking(Entity entity) {
		TRACKED.remove(entity.getId());
	}

	static void tick(MinecraftServer server, int now) {
		if (TRACKED.isEmpty()) {
			return;
		}

		TRACKED.int2ObjectEntrySet().removeIf(entry -> {
			Tracked tracked = entry.getValue();
			Entity entity = tracked.level.getEntity(entry.getIntKey());
			if (entity == null || !entity.isAlive() || now > tracked.until) {
				return true;
			}

			Vec3 pos = entity.position();
			Vec3 motion = pos.subtract(tracked.lastPos);
			Vec3 last = tracked.lastMotion;
			double horizontal = Math.sqrt(last.x * last.x + last.z * last.z);
			boolean wall = entity.horizontalCollision && horizontal > ForceTuning.IMPACT_MIN_SPEED;
			boolean ceiling = entity.verticalCollision && !entity.verticalCollisionBelow && last.y > ForceTuning.IMPACT_MIN_SPEED;

			if (wall || ceiling) {
				ServerPlayer attacker = tracked.attacker != null ? server.getPlayerList().getPlayer(tracked.attacker) : null;
				impact(tracked.level, entity, wall ? new Vec3(last.x, 0, last.z) : new Vec3(0, last.y, 0), attacker, tracked.scale);
				return true;
			}

			// Nach dem Aufsetzen auf dem Boden ist der Flug vorbei.
			if (entity.onGround() && motion.horizontalDistanceSqr() < 0.01 && now > tracked.until - ForceTuning.IMPACT_TRACK_TICKS + 4) {
				return true;
			}

			tracked.lastPos = pos;
			tracked.lastMotion = motion;
			return false;
		});
	}

	private static void impact(ServerLevel level, Entity entity, Vec3 velocity, @Nullable ServerPlayer attacker, float scale) {
		double speed = velocity.length();
		float damage = (float) Math.min(ForceTuning.IMPACT_MAX_DAMAGE,
				(speed - ForceTuning.IMPACT_MIN_SPEED + 0.25) * ForceTuning.IMPACT_DAMAGE_PER_SPEED * scale);

		if (entity instanceof LivingEntity living && damage > 0.5f) {
			ModDamageTypes.skipHurtCooldown(living);
			living.hurtServer(level, ModDamageTypes.forceImpact(level, attacker), damage);
		}

		// Staub und Brocken der getroffenen Wand.
		Vec3 dir = velocity.normalize();
		Vec3 center = ForceTargeting.center(entity);
		Vec3 contact = center.add(dir.scale(entity.getBbWidth() * 0.5 + 0.25));
		BlockState state = level.getBlockState(BlockPos.containing(contact));
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), contact.x, contact.y, contact.z, 24, 0.3, 0.3, 0.3, 0.15);
		}
		level.sendParticles(ParticleTypes.POOF, contact.x, contact.y, contact.z, 8, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, contact.x, contact.y, contact.z, ModSounds.FORCE_IMPACT, SoundSource.PLAYERS,
				(float) Math.min(1.4, 0.6 + speed * 0.4), 0.85f + level.getRandom().nextFloat() * 0.2f);

		Entity source = attacker != null ? attacker : entity;
		ModNetworking.broadcastForceFx(entity, new ForceFxPayload(source.getId(), ForceAbilities.PUSH.id(), ForceFxPayload.IMPACT, 1,
				List.of(entity.getId()), List.of((int) (speed * 100))));
	}

	static void clear() {
		TRACKED.clear();
	}
}
