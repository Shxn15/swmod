package com.shxn15.swmod.force.ability;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;

import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceContext;
import com.shxn15.swmod.force.ForceSide;
import com.shxn15.swmod.force.ForceStat;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.registry.ModSounds;

import static com.shxn15.swmod.force.ForceTuning.*;

/**
 * Machtheilung: heilt sofort und regeneriert danach noch etwas. Ab Stufe 2 werden Gift und
 * Wither vertrieben, ab Stufe 3 heilt man auch Mitspieler und eigene Tiere in der Nähe.
 */
public class HealAbility extends ForceAbility {
	public HealAbility() {
		super(Activation.INSTANT, ForceSide.LIGHT, 0x6CFF9A, 3);
	}

	@Override
	public int learnCost(int level) {
		return at(HEAL_LEARN, level);
	}

	@Override
	public float cost(int level) {
		return at(HEAL_COST, level);
	}

	@Override
	public int cooldown(int level) {
		return at(HEAL_COOLDOWN, level);
	}

	@Override
	public List<ForceStat> stats() {
		return List.of(
				ForceStat.hearts("heal", level -> at(HEAL_AMOUNT, level)),
				ForceStat.seconds("regeneration", level -> at(HEAL_REGEN_TICKS, level)),
				ForceStat.flag("cleanse", level -> level >= HEAL_CLEANSE_LEVEL),
				ForceStat.flag("allies", level -> level >= HEAL_ALLIES_LEVEL),
				ForceStat.seconds("cooldown", level -> at(HEAL_COOLDOWN, level)));
	}

	@Override
	public boolean cast(ForceContext context) {
		ServerPlayer player = context.player();
		ServerLevel level = context.world();
		int lvl = context.level();
		boolean cleanse = lvl >= HEAL_CLEANSE_LEVEL && (player.hasEffect(MobEffects.POISON) || player.hasEffect(MobEffects.WITHER));

		List<LivingEntity> allies = new ArrayList<>();
		if (lvl >= HEAL_ALLIES_LEVEL) {
			for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(HEAL_ALLY_RADIUS), this::isAlly)) {
				LivingEntity ally = (LivingEntity) entity;
				if (ally.getHealth() < ally.getMaxHealth() && (!(ally instanceof OwnableEntity ownable) || ownable.getOwner() == player)) {
					allies.add(ally);
				}
			}
		}

		if (player.getHealth() >= player.getMaxHealth() && !cleanse && allies.isEmpty()) {
			context.event(ForceEventPayload.NOTHING_TO_HEAL);
			return false;
		}

		heal(player, at(HEAL_AMOUNT, lvl), at(HEAL_REGEN_TICKS, lvl));
		if (cleanse) {
			player.removeEffect(MobEffects.POISON);
			player.removeEffect(MobEffects.WITHER);
		}

		List<Integer> healed = new ArrayList<>();
		healed.add(player.getId());
		for (LivingEntity ally : allies) {
			heal(ally, at(HEAL_AMOUNT, lvl) * 0.5f, at(HEAL_REGEN_TICKS, lvl) / 2);
			healed.add(ally.getId());
		}

		context.fx(ForceFxPayload.CAST, healed);
		context.playSound(ModSounds.FORCE_HEAL, 1.0f, 1.0f);
		return true;
	}

	private boolean isAlly(Entity entity) {
		return entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator()
				&& (entity instanceof Player || entity instanceof OwnableEntity ownable && ownable.getOwner() != null);
	}

	private static void heal(LivingEntity entity, float amount, int regenerationTicks) {
		entity.heal(amount);
		entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regenerationTicks, 0, false, true));
	}
}
