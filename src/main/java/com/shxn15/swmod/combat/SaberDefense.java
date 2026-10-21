package com.shxn15.swmod.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModSounds;

/** Blocken und Parieren: gegen Lichtschwerter, Nahkampf von Mobs und Geschosse. */
public final class SaberDefense {
	private SaberDefense() {
	}

	/**
	 * Ein Lichtschwerthieb trifft einen Spieler. Hält dieser die Deckung in Richtung des Angreifers,
	 * wird geblockt oder (bei gutem Timing) perfekt pariert.
	 *
	 * @return true, wenn der Hieb abgefangen wurde
	 */
	static boolean tryBlockStrike(ServerPlayer attacker, SaberCombatState attackerState, ServerPlayer defender, Vec3 contact, int now) {
		SaberCombatState defenderState = SaberCombatSystem.state(defender);
		if (!SaberCombatSystem.isBlocking(defender, defenderState, now) || !SaberCombatSystem.isInFront(defender, attacker.getEyePosition())) {
			return false;
		}

		ServerLevel level = defender.level();
		int attackerColor = Lightsabers.color(attacker.getMainHandItem());
		int defenderColor = Lightsabers.color(defender.getUseItem());
		SwingDirection dir = attackerState.move != null ? attackerState.move.direction() : SwingDirection.STAB;

		if (defender.getTicksUsingItem() <= SaberTuning.PARRY_WINDOW_TICKS) {
			// Perfekte Parade: der Angreifer taumelt, der Verteidiger darf schnell kontern.
			SaberCombatSystem.stagger(attacker, attackerState, SaberTuning.PARRY_STAGGER_TICKS, now);
			defenderState.addBlockPoints(SaberTuning.PARRY_REWARD);
			defenderState.riposteUntil = now + SaberTuning.RIPOSTE_WINDOW_TICKS;
			ModNetworking.broadcastAnim(defender, SaberAnimation.PARRY, dir, 8, true);

			level.playSound(null, contact.x, contact.y, contact.z, ModSounds.LIGHTSABER_PARRY, SoundSource.PLAYERS, 1.3f, 1.0f);
			SaberCombatSystem.sparks(level, contact, 40, attackerColor, defenderColor);
			SaberCombatSystem.push(attacker, defender, 0.4);

			defender.sendOverlayMessage(Component.translatable("message.swmod.perfect_parry").withStyle(ChatFormatting.GOLD));
			attacker.sendOverlayMessage(Component.translatable("message.swmod.parried").withStyle(ChatFormatting.RED));
			return true;
		}

		defenderState.drainBlockPoints(SaberTuning.BLOCK_COST_SABER, now);
		SaberCombatSystem.recoil(attacker, attackerState, SaberTuning.DEFLECT_RECOIL_TICKS, SaberAnimation.RECOIL, now);

		level.playSound(null, contact.x, contact.y, contact.z, ModSounds.LIGHTSABER_BLOCK, SoundSource.PLAYERS, 1.1f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
		SaberCombatSystem.sparks(level, contact, 18, attackerColor, defenderColor);

		if (defenderState.blockPoints <= 0) {
			SaberCombatSystem.guardBreak(defender, defenderState, attacker, now);
		} else {
			ModNetworking.broadcastAnim(defender, SaberAnimation.BLOCK_HIT, dir, 5, true);
		}
		return true;
	}

	/** Ein geworfenes Lichtschwert fliegt auf einen blockenden Spieler zu. */
	public static boolean tryBlockThrown(ServerPlayer defender, ThrownLightsaberEntity saber) {
		SaberCombatState state = SaberCombatSystem.state(defender);
		int now = SaberCombatSystem.now();
		if (!SaberCombatSystem.isBlocking(defender, state, now) || !SaberCombatSystem.isInFront(defender, saber.position())) {
			return false;
		}

		boolean perfect = defender.getTicksUsingItem() <= SaberTuning.PARRY_WINDOW_TICKS;
		state.drainBlockPoints(perfect ? 0 : SaberTuning.BLOCK_COST_PROJECTILE * 2, now);
		ServerLevel level = defender.level();
		level.playSound(null, saber.getX(), saber.getY(), saber.getZ(), ModSounds.LIGHTSABER_BLOCK, SoundSource.PLAYERS, 1.1f, 1.1f);
		SaberCombatSystem.sparks(level, saber.position(), 20, Lightsabers.color(saber.getStack()), Lightsabers.color(defender.getUseItem()));
		ModNetworking.broadcastAnim(defender, perfect ? SaberAnimation.PARRY : SaberAnimation.BLOCK_HIT, SwingDirection.STAB, 6, true);

		if (state.blockPoints <= 0 && saber.getOwner() instanceof ServerPlayer thrower) {
			SaberCombatSystem.guardBreak(defender, state, thrower, now);
		}
		return true;
	}

	/**
	 * Machtblitze treffen einen Spieler. Hält er die Deckung in Richtung des Wirkenden, fängt die
	 * Klinge die Blitze ab (kostet Blockpunkte) – wie Mace Windu gegen Palpatine.
	 *
	 * @return true, wenn die Blitze abgefangen wurden
	 */
	public static boolean tryBlockLightning(ServerPlayer defender, ServerPlayer caster, float cost) {
		SaberCombatState state = SaberCombatSystem.state(defender);
		int now = SaberCombatSystem.now();
		if (!SaberCombatSystem.isBlocking(defender, state, now) || !SaberCombatSystem.isInFront(defender, caster.getEyePosition())) {
			return false;
		}

		state.drainBlockPoints(cost, now);
		ServerLevel level = defender.level();
		Vec3 point = defender.getEyePosition().add(defender.getLookAngle().scale(0.7)).add(0, -0.2, 0);
		SaberCombatSystem.sparks(level, point, 6, Lightsabers.color(defender.getUseItem()), 0xC8D8FF);
		if (level.getRandom().nextInt(3) == 0) {
			level.playSound(null, point.x, point.y, point.z, ModSounds.LIGHTSABER_DEFLECT, SoundSource.PLAYERS, 0.6f,
					0.8f + level.getRandom().nextFloat() * 0.4f);
		}

		if (state.blockPoints <= 0) {
			SaberCombatSystem.guardBreak(defender, state, caster, now);
		}
		return true;
	}

	/**
	 * Fabric-Event: wird vor jedem Schaden an einem Wesen aufgerufen.
	 * Blockt Nahkampf und Geschosse von vorne, wenn ein Spieler mit dem Lichtschwert die Deckung hält.
	 */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer defender) || ModDamageTypes.isLightsaber(source) || ModDamageTypes.isForce(source)
				|| source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return true;
		}

		Entity direct = source.getDirectEntity();
		if (direct == null) {
			return true;
		}

		SaberCombatState state = SaberCombatSystem.state(defender);
		int now = SaberCombatSystem.now();
		Vec3 from = source.getSourcePosition() != null ? source.getSourcePosition() : direct.position();
		if (!SaberCombatSystem.isBlocking(defender, state, now) || !SaberCombatSystem.isInFront(defender, from)) {
			return true;
		}

		boolean perfect = defender.getTicksUsingItem() <= SaberTuning.PARRY_WINDOW_TICKS;
		ServerLevel level = defender.level();
		int color = Lightsabers.color(defender.getUseItem());
		Vec3 point = defender.getEyePosition().add(defender.getLookAngle().scale(0.7)).add(0, -0.25, 0);

		if (direct instanceof Projectile projectile) {
			state.drainBlockPoints(perfect ? 0 : SaberTuning.BLOCK_COST_PROJECTILE, now);
			reflect(projectile, defender, perfect);
			level.playSound(null, point.x, point.y, point.z, ModSounds.LIGHTSABER_DEFLECT, SoundSource.PLAYERS, 1.0f,
					0.9f + level.getRandom().nextFloat() * 0.25f);
			SaberCombatSystem.sparks(level, point, 10, color, 0xFFFFFF);
		} else {
			state.drainBlockPoints(perfect ? 0 : SaberTuning.BLOCK_COST_MELEE, now);
			if (direct instanceof LivingEntity attacker) {
				SaberCombatSystem.push(attacker, defender, perfect ? 0.8 : 0.45);
			}
			level.playSound(null, point.x, point.y, point.z, ModSounds.LIGHTSABER_BLOCK, SoundSource.PLAYERS, 1.0f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
			SaberCombatSystem.sparks(level, point, 14, color, 0xFFFFFF);
			ModNetworking.broadcastAnim(defender, perfect ? SaberAnimation.PARRY : SaberAnimation.BLOCK_HIT, SwingDirection.STAB, 5, true);
		}

		if (state.blockPoints <= 0 && source.getEntity() instanceof ServerPlayer attackerPlayer) {
			SaberCombatSystem.guardBreak(defender, state, attackerPlayer, now);
		} else if (state.blockPoints <= 0) {
			state.guardLockedUntil = now + SaberTuning.GUARD_BREAK_STUN_TICKS;
			state.statusDirty = true;
		}
		return false;
	}

	/**
	 * Lenkt ein Geschoss zurück. Bei perfektem Timing fliegt es genau dorthin, wo der Spieler hinschaut
	 * (wie ein Jedi, der Blasterschüsse zurückschickt), sonst in eine ungefähre Richtung.
	 * Erst im nächsten Tick, weil Vanilla abgewehrte Pfeile im selben Tick noch selbst abbremst.
	 */
	private static void reflect(Projectile projectile, ServerPlayer defender, boolean perfect) {
		RandomSource random = defender.getRandom();
		Vec3 look = defender.getLookAngle();
		Vec3 direction = perfect ? look : look.add(
				(random.nextFloat() - 0.5f) * 0.7f,
				(random.nextFloat() - 0.2f) * 0.4f,
				(random.nextFloat() - 0.5f) * 0.7f).normalize();
		double speed = Math.max(1.2, projectile.getDeltaMovement().length());

		SaberCombatSystem.runNextTick(() -> {
			if (projectile.isRemoved()) {
				return;
			}
			projectile.setOwner(defender);
			projectile.setDeltaMovement(direction.scale(speed));
			projectile.needsSync = true;
		});
	}
}
