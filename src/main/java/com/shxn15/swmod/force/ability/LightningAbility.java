package com.shxn15.swmod.force.ability;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.combat.SaberDefense;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceMotion;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.force.ForceTargeting;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtblitze: solange man die rechte Maustaste hält, zucken Blitze aus der Hand in alle Gegner
 * im Blickkegel. Ohne Ziel fahren sie knisternd in die Luft. Auf Stufe 3 springen sie auf weitere
 * Gegner über. Ein Lichtschwert in Deckung fängt sie ab.
 *
 * <p>Die Effekt-Zusatzdaten sind: Anzahl Ketten, dann je Kette (von, nach), dann Anzahl
 * geblockter Ziele und deren IDs.
 */
public class LightningAbility extends ForceAbility {
	public LightningAbility() {
		super(Activation.CHANNEL, ForceSide.DARK, 0xB98CFF, 3);
	}

	static final class Data {
		IntList targets = new IntArrayList();
		IntList chains = new IntArrayList();
		IntList blocked = new IntArrayList();
		int ticks;
	}

	@Override
	public int learnCost(int level) {
		return at(LIGHTNING_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(LIGHTNING_COST, level);
	}

	@Override
	public float drain(int level) {
		return at(LIGHTNING_DRAIN, level);
	}

	@Override
	public int cooldown(int level) {
		return at(LIGHTNING_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.blocks("range", level -> at(LIGHTNING_RANGE, level)),
				ForceStat.number("targets", level -> at(LIGHTNING_TARGETS, level) + at(LIGHTNING_CHAINS, level) * at(LIGHTNING_TARGETS, level)),
				ForceStat.hearts("dps", level -> at(LIGHTNING_DAMAGE, level) * 20f / LIGHTNING_HIT_INTERVAL),
				ForceStat.flag("chain", level -> at(LIGHTNING_CHAINS, level) > 0),
				ForceStat.number("drain", level -> at(LIGHTNING_DRAIN, level) * 20));
	}

	@Override
	public @Nullable Object startChannel(ForceContext context) {
		Data data = new Data();
		retarget(context, data);
		context.fx(ForceFxPayload.CHANNEL_START, data.targets, extra(data));
		context.playSound(ModSounds.FORCE_LIGHTNING, 1.0f, 0.9f + context.world().getRandom().nextFloat() * 0.2f);
		return data;
	}

	@Override
	public boolean tickChannel(ForceContext context, Object raw) {
		Data data = (Data) raw;
		data.ticks++;

		if (data.ticks % 2 == 0) {
			IntList oldTargets = new IntArrayList(data.targets);
			IntList oldChains = new IntArrayList(data.chains);
			IntList oldBlocked = new IntArrayList(data.blocked);
			retarget(context, data);
			if (data.ticks % LIGHTNING_HIT_INTERVAL == 0) {
				this.strike(context, data);
			}
			if (!oldTargets.equals(data.targets) || !oldChains.equals(data.chains) || !oldBlocked.equals(data.blocked)) {
				context.fx(ForceFxPayload.CHANNEL_UPDATE, data.targets, extra(data));
			}
		}
		return true;
	}

	@Override
	public void stopChannel(ForceContext context, Object raw) {
		context.fx(ForceFxPayload.CHANNEL_END, List.of());
	}

	private static void retarget(ForceContext context, Data data) {
		ServerPlayer player = context.player();
		int lvl = context.level();
		List<Entity> inCone = ForceTargeting.cone(player, at(LIGHTNING_RANGE, lvl), at(LIGHTNING_ANGLE, lvl),
				entity -> ForceTargeting.isLivingTarget(entity) && !ForceTargeting.isFriendly(player, entity));

		IntList targets = new IntArrayList();
		for (int i = 0; i < Math.min(inCone.size(), at(LIGHTNING_TARGETS, lvl)); i++) {
			targets.add(inCone.get(i).getId());
		}

		// Kettenblitze springen vom Ziel auf weitere Gegner in der Nähe.
		IntList chains = new IntArrayList();
		int chainCount = at(LIGHTNING_CHAINS, lvl);
		if (chainCount > 0) {
			List<Integer> used = new ArrayList<>(targets);
			for (int id : targets.toIntArray()) {
				Entity from = context.world().getEntity(id);
				if (from == null) continue;
				int added = 0;
				for (Entity next : context.world().getEntities(from, from.getBoundingBox().inflate(LIGHTNING_CHAIN_RANGE),
						entity -> ForceTargeting.isLivingTarget(entity) && !ForceTargeting.isFriendly(player, entity))) {
					if (added >= chainCount || used.contains(next.getId())) continue;
					chains.add(id);
					chains.add(next.getId());
					used.add(next.getId());
					added++;
				}
			}
		}

		data.targets = targets;
		data.chains = chains;
	}

	private void strike(ForceContext context, Data data) {
		ServerPlayer player = context.player();
		ServerLevel level = context.world();
		float damage = at(LIGHTNING_DAMAGE, context.level());
		IntList blocked = new IntArrayList();

		IntList all = new IntArrayList(data.targets);
		for (int i = 1; i < data.chains.size(); i += 2) {
			all.add(data.chains.getInt(i));
		}

		for (int id : all.toIntArray()) {
			if (!(level.getEntity(id) instanceof LivingEntity target) || !target.isAlive()) continue;

			if (target instanceof ServerPlayer defender && data.targets.contains(id)
					&& SaberDefense.tryBlockLightning(defender, player, LIGHTNING_BLOCK_COST)) {
				blocked.add(id);
				continue;
			}

			ModDamageTypes.skipHurtCooldown(target);
			// Kettenblitze sind etwas schwächer als der direkte Strahl.
			float amount = data.targets.contains(id) ? damage : damage * 0.6f;
			if (target.hurtServer(level, ModDamageTypes.forceLightning(level, player), amount)) {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 1, false, false));
				Vec3 away = ForceTargeting.center(target).subtract(player.getEyePosition()).normalize();
				Vec3 jolt = away.scale(0.06 * ForceMotion.mobility(target)).add(0, 0.04, 0);
				ForceMotion.setVelocity(target, target.getDeltaMovement().add(jolt));
				Vec3 center = ForceTargeting.center(target);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 6, target.getBbWidth() * 0.4,
						target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.3);
				if (level.getRandom().nextInt(3) == 0) {
					level.playSound(null, center.x, center.y, center.z, ModSounds.FORCE_LIGHTNING_HIT, SoundSource.PLAYERS, 0.7f,
							0.8f + level.getRandom().nextFloat() * 0.4f);
				}
			}
		}
		data.blocked = blocked;
	}

	private static List<Integer> extra(Data data) {
		List<Integer> extra = new ArrayList<>();
		extra.add(data.chains.size() / 2);
		extra.addAll(data.chains);
		extra.add(data.blocked.size());
		extra.addAll(data.blocked);
		return extra;
	}
}
