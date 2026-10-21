package com.shxn15.swmod.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.shxn15.swmod.lightsaber.LightsaberData;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.lightsaber.ThrownLightsaberEntity;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModDamageTypes;
import com.shxn15.swmod.registry.ModDataComponents;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Das serverseitige Herz des Lichtschwertkampfs. Der Server ist die Autorität für Treffer,
 * Blocks, Paraden und Klingenzusammenstöße; Clients sagen nur ihre eigenen Animationen voraus.
 */
public final class SaberCombatSystem {
	/** Unterschritte pro Tick für die Trefferprüfung, damit schnelle Klingen nichts "überspringen". */
	private static final int SUB_STEPS = 4;

	private static final Map<UUID, SaberCombatState> STATES = new HashMap<>();
	private static final List<Runnable> NEXT_TICK = new ArrayList<>();
	private static @Nullable MinecraftServer server;

	private SaberCombatSystem() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SaberCombatSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> STATES.remove(handler.player.getUUID()));

		// Mit gezündetem Lichtschwert ersetzt unser System den normalen Nahkampf und das Abbauen.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				Lightsabers.isReadyInMainHand(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				Lightsabers.isReadyInMainHand(player) ? InteractionResult.FAIL : InteractionResult.PASS);

		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SaberDefense::allowDamage);
	}

	static SaberCombatState state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), uuid -> new SaberCombatState());
	}

	static int now() {
		return server != null ? server.getTickCount() : 0;
	}

	/** Führt eine Aufgabe zu Beginn des nächsten Server-Ticks aus. */
	static void runNextTick(Runnable runnable) {
		NEXT_TICK.add(runnable);
	}

	// ------------------------------------------------------------------------------------------
	// Anfragen vom Client
	// ------------------------------------------------------------------------------------------

	public static void requestSwing(ServerPlayer player, SwingDirection direction) {
		if (!Lightsabers.isReadyInMainHand(player) || player.isUsingItem()) {
			return;
		}

		SaberCombatState state = state(player);
		int now = now();
		if (now < state.stunnedUntil) {
			return;
		}

		if (state.canChainNow()) {
			startMove(player, state, direction, now);
		} else {
			// Während Ausholen/Treffphase: den nächsten Schlag vormerken (Kombo-Puffer).
			state.queued = direction;
		}
	}

	public static void toggle(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!Lightsabers.isLightsaber(stack) || Lightsabers.isThrown(stack, player.level())) {
			return;
		}

		LightsaberData data = Lightsabers.data(stack);
		boolean ignite = !data.ignited();
		stack.set(ModDataComponents.LIGHTSABER, data.withIgnited(ignite));

		if (!ignite) {
			SaberCombatState state = STATES.get(player.getUUID());
			if (state != null) {
				state.cancelMove();
			}
			if (player.isUsingItem()) {
				player.stopUsingItem();
			}
			ModNetworking.broadcastAnim(player, SaberAnimation.CANCEL, SwingDirection.STAB, 0, false);
		}

		// Der eigene Client spielt den Sound schon selbst ab.
		player.level().playSound(player, player.getX(), player.getEyeY(), player.getZ(),
				ignite ? ModSounds.LIGHTSABER_IGNITE : ModSounds.LIGHTSABER_RETRACT, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	public static void throwSaber(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!Lightsabers.isReady(stack, player.level()) || player.isUsingItem()) {
			return;
		}

		SaberCombatState state = state(player);
		int now = now();
		if (now < state.stunnedUntil || now < state.throwCooldownUntil || state.isCommitted()) {
			return;
		}

		state.cancelMove();
		ServerLevel level = player.level();
		int throwId = 1 + level.getRandom().nextInt(Integer.MAX_VALUE - 1);
		LightsaberData data = Lightsabers.data(stack);
		stack.set(ModDataComponents.LIGHTSABER, data.withThrow(level.getGameTime() + SaberTuning.THROW_LOCK_TICKS, throwId));

		ItemStack display = stack.copyWithCount(1);
		display.set(ModDataComponents.LIGHTSABER, data.caught().withIgnited(true));
		level.addFreshEntity(new ThrownLightsaberEntity(level, player, display, throwId));

		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.LIGHTSABER_THROW, SoundSource.PLAYERS, 1.0f, 1.0f);
		ModNetworking.broadcastAnim(player, SaberAnimation.THROW, SwingDirection.STAB, 6, true);
	}

	/** Das geworfene Schwert ist zurück in der Hand. */
	public static void onCaught(Player player) {
		if (player instanceof ServerPlayer serverPlayer) {
			state(serverPlayer).throwCooldownUntil = now() + SaberTuning.THROW_COOLDOWN_TICKS;
			ModNetworking.broadcastAnim(serverPlayer, SaberAnimation.CATCH, SwingDirection.STAB, 6, true);
		}
	}

	/** Darf der Spieler gerade die Deckung heben? (Aufruf aus {@code LightsaberItem#use}) */
	public static boolean canStartBlocking(Player player) {
		SaberCombatState state = STATES.get(player.getUUID());
		if (state == null) {
			return true;
		}

		int now = now();
		if (now < state.guardLockedUntil || now < state.stunnedUntil || state.isCommitted()) {
			return false;
		}

		// Blocken bricht die Erholungsphase eines Schlags ab.
		state.cancelMove();
		return true;
	}

	// ------------------------------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer srv) {
		server = srv;
		int now = srv.getTickCount();

		if (!NEXT_TICK.isEmpty()) {
			List<Runnable> tasks = new ArrayList<>(NEXT_TICK);
			NEXT_TICK.clear();
			tasks.forEach(Runnable::run);
		}

		List<ServerPlayer> swinging = new ArrayList<>();

		for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
			boolean ready = Lightsabers.isReadyInMainHand(player);
			SaberCombatState state = ready ? state(player) : STATES.get(player.getUUID());
			if (state == null) {
				continue;
			}

			updateStatus(player, state, ready, now);

			if (state.move == null && state.queued != null && ready && now >= state.stunnedUntil) {
				SwingDirection next = state.queued;
				state.queued = null;
				startMove(player, state, next, now);
			}

			if (state.move != null && state.queued != null && state.canChainNow()) {
				SwingDirection next = state.queued;
				state.queued = null;
				startMove(player, state, next, now);
			}

			// Das "Wusch" spielt jeder Client selbst passend zur Animation ab (SaberAnimState).
			if (state.isInActivePhase()) {
				state.segments = computeSegments(player, state);
				swinging.add(player);
			} else {
				state.segments = null;
			}
		}

		resolveClashes(swinging, now);

		for (ServerPlayer player : swinging) {
			SaberCombatState state = state(player);
			if (!state.interrupted && state.move != null && state.segments != null) {
				performHits(player, state, now);
			}
			state.interrupted = false;
		}

		// Zeit voranschreiten lassen.
		for (SaberCombatState state : STATES.values()) {
			if (state.move != null) {
				state.moveTick++;
				if (state.moveTick >= state.move.total()) {
					state.move = null;
					state.hitThisMove.clear();
				}
			}
		}
	}

	private static void updateStatus(ServerPlayer player, SaberCombatState state, boolean ready, int now) {
		if (!ready && state.move != null) {
			state.cancelMove();
		}
		if (now < state.stunnedUntil && state.move != null) {
			state.cancelMove();
		}

		boolean blocking = isBlocking(player, state, now);
		if (now < state.guardLockedUntil && player.isUsingItem() && Lightsabers.isLightsaber(player.getUseItem())) {
			player.stopUsingItem();
		}

		if (now - state.lastBlockHitTick > SaberTuning.BLOCK_REGEN_DELAY && state.blockPoints < SaberTuning.MAX_BLOCK_POINTS) {
			state.blockPoints = Math.min(SaberTuning.MAX_BLOCK_POINTS,
					state.blockPoints + (blocking ? SaberTuning.BLOCK_REGEN_WHILE_BLOCKING : SaberTuning.BLOCK_REGEN));
		}

		boolean changed = Math.abs(state.blockPoints - state.sentBlockPoints) >= 1f
				|| (state.blockPoints >= SaberTuning.MAX_BLOCK_POINTS && state.sentBlockPoints < SaberTuning.MAX_BLOCK_POINTS);
		if (state.statusDirty || changed || now - state.lastStatusSync > 40) {
			if (ready || state.statusDirty || changed) {
				ModNetworking.sendStatus(player, state.blockPoints,
						Math.max(0, state.stunnedUntil - now), Math.max(0, state.guardLockedUntil - now));
			}
			state.sentBlockPoints = state.blockPoints;
			state.statusDirty = false;
			state.lastStatusSync = now;
		}
	}

	private static void startMove(ServerPlayer player, SaberCombatState state, SwingDirection direction, int now) {
		SwingDirection previous = state.move != null ? state.move.direction() : null;
		state.move = SaberMove.create(direction, previous, now < state.riposteUntil);
		state.moveTick = 0;
		state.lastDirection = direction;
		state.riposteUntil = 0;
		state.hitThisMove.clear();
		state.segments = null;

		ModNetworking.broadcastAnim(player, SaberAnimation.SWING, direction, state.move.windup(), false);
	}

	private static Vec3[][] computeSegments(ServerPlayer player, SaberCombatState state) {
		SaberMove move = state.move;
		SaberFrame frame = SaberFrame.of(player);
		int activeTick = state.moveTick - move.activeStart();
		Vec3[][] segments = new Vec3[SUB_STEPS + 1][];

		for (int k = 0; k <= SUB_STEPS; k++) {
			float u = (activeTick + k / (float) SUB_STEPS) / move.active();
			SaberPose pose = SaberGeometry.activePose(move, u);
			segments[k] = new Vec3[]{frame.bladeBase(pose), frame.bladeTip(pose, 1f)};
		}

		return segments;
	}

	// ------------------------------------------------------------------------------------------
	// Zusammenstöße & Treffer
	// ------------------------------------------------------------------------------------------

	private static void resolveClashes(List<ServerPlayer> swinging, int now) {
		for (int i = 0; i < swinging.size(); i++) {
			ServerPlayer a = swinging.get(i);
			SaberCombatState sa = state(a);
			if (sa.interrupted) continue;

			for (int j = i + 1; j < swinging.size(); j++) {
				ServerPlayer b = swinging.get(j);
				SaberCombatState sb = state(b);
				if (sb.interrupted || a.level() != b.level() || a.distanceToSqr(b) > 36) continue;

				for (int k = 0; k < sa.segments.length; k++) {
					Vec3[] segA = sa.segments[k];
					Vec3[] segB = sb.segments[k];
					if (SaberGeometry.segmentDistance(segA[0], segA[1], segB[0], segB[1]) < SaberTuning.CLASH_DISTANCE) {
						Vec3 point = segA[0].add(segA[1]).add(segB[0]).add(segB[1]).scale(0.25);
						clash(a, sa, b, sb, point, now);
						break;
					}
				}
			}
		}
	}

	private static void clash(ServerPlayer a, SaberCombatState sa, ServerPlayer b, SaberCombatState sb, Vec3 point, int now) {
		recoil(a, sa, SaberTuning.CLASH_RECOIL_TICKS, SaberAnimation.CLASH, now);
		recoil(b, sb, SaberTuning.CLASH_RECOIL_TICKS, SaberAnimation.CLASH, now);
		sa.interrupted = true;
		sb.interrupted = true;

		ServerLevel level = a.level();
		level.playSound(null, point.x, point.y, point.z, ModSounds.LIGHTSABER_CLASH, SoundSource.PLAYERS, 1.2f,
				0.9f + level.getRandom().nextFloat() * 0.2f);
		sparks(level, point, 26, Lightsabers.color(a.getMainHandItem()), Lightsabers.color(b.getMainHandItem()));

		push(a, b, 0.3);
		push(b, a, 0.3);
	}

	private static void performHits(ServerPlayer attacker, SaberCombatState state, int now) {
		ServerLevel level = attacker.level();
		float reach = SaberGeometry.GRIP_TO_EMITTER + SaberGeometry.BLADE_LENGTH + 1.5f;
		AABB area = attacker.getBoundingBox().inflate(reach);
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != attacker && e.isAlive() && !e.isSpectator() && !state.hitThisMove.contains(e.getId()));
		if (candidates.isEmpty()) {
			return;
		}

		for (Vec3[] segment : state.segments) {
			Vec3 base = segment[0];
			Vec3 tip = segment[1];

			for (LivingEntity target : candidates) {
				if (state.hitThisMove.contains(target.getId())) continue;

				AABB box = target.getBoundingBox().inflate(SaberTuning.HIT_TOLERANCE);
				Vec3 contact = box.contains(base) ? base : box.clip(base, tip).orElse(null);
				if (contact == null || !canReach(attacker, base, contact)) continue;

				state.hitThisMove.add(target.getId());
				if (strike(attacker, state, target, contact, now)) {
					// Geblockt oder pariert: der Schlag ist vorbei.
					return;
				}
			}
		}
	}

	/** Die Klinge geht nicht durch Wände. */
	private static boolean canReach(ServerPlayer attacker, Vec3 bladeBase, Vec3 contact) {
		Vec3 from = attacker.getEyePosition();
		HitResult result = attacker.level().clip(new ClipContext(from, contact, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
		if (result.getType() == HitResult.Type.MISS) {
			return true;
		}
		HitResult fromBlade = attacker.level().clip(new ClipContext(bladeBase, contact, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, attacker));
		return fromBlade.getType() == HitResult.Type.MISS;
	}

	/** @return true, wenn der Schlag abgefangen wurde und damit endet */
	private static boolean strike(ServerPlayer attacker, SaberCombatState state, LivingEntity target, Vec3 contact, int now) {
		if (target instanceof ServerPlayer defender && SaberDefense.tryBlockStrike(attacker, state, defender, contact, now)) {
			return true;
		}

		ServerLevel level = attacker.level();
		target.invulnerableTime = 0; // Kombos sollen ohne Unverwundbarkeits-Pause treffen
		if (target.hurtServer(level, ModDamageTypes.lightsaber(level, attacker), SaberTuning.DAMAGE)) {
			level.playSound(null, contact.x, contact.y, contact.z, ModSounds.LIGHTSABER_HIT, SoundSource.PLAYERS, 1.0f,
					0.9f + level.getRandom().nextFloat() * 0.2f);
			level.sendParticles(ParticleTypes.SMOKE, contact.x, contact.y, contact.z, 6, 0.08, 0.08, 0.08, 0.01);
			level.sendParticles(new DustParticleOptions(Lightsabers.color(attacker.getMainHandItem()), 1.0f),
					contact.x, contact.y, contact.z, 10, 0.12, 0.12, 0.12, 0.0);
		}
		return false;
	}

	// ------------------------------------------------------------------------------------------
	// Reaktionen
	// ------------------------------------------------------------------------------------------

	/** Schlag abgebrochen, kurzes Zurückprallen. */
	static void recoil(ServerPlayer player, SaberCombatState state, int ticks, SaberAnimation animation, int now) {
		SwingDirection dir = state.move != null ? state.move.direction() : state.lastDirection;
		state.cancelMove();
		state.stunnedUntil = Math.max(state.stunnedUntil, now + ticks);
		state.statusDirty = true;
		ModNetworking.broadcastAnim(player, animation, dir != null ? dir : SwingDirection.STAB, ticks, true);
	}

	/** Nach einer perfekten Parade: Taumeln, weder Angriff noch Block möglich. */
	static void stagger(ServerPlayer player, SaberCombatState state, int ticks, int now) {
		SwingDirection dir = state.move != null ? state.move.direction() : state.lastDirection;
		state.cancelMove();
		state.stunnedUntil = Math.max(state.stunnedUntil, now + ticks);
		lockGuard(player, state, ticks, now);
		ModNetworking.broadcastAnim(player, SaberAnimation.STAGGER, dir != null ? dir : SwingDirection.STAB, ticks, true);
	}

	static void guardBreak(ServerPlayer defender, SaberCombatState state, ServerPlayer attacker, int now) {
		state.blockPoints = 0;
		state.stunnedUntil = Math.max(state.stunnedUntil, now + SaberTuning.GUARD_BREAK_STUN_TICKS / 2);
		lockGuard(defender, state, SaberTuning.GUARD_BREAK_STUN_TICKS, now);
		ModNetworking.broadcastAnim(defender, SaberAnimation.GUARD_BREAK, SwingDirection.STAB, SaberTuning.GUARD_BREAK_STUN_TICKS, true);

		defender.level().playSound(null, defender.getX(), defender.getEyeY(), defender.getZ(), ModSounds.LIGHTSABER_GUARD_BREAK,
				SoundSource.PLAYERS, 1.0f, 1.0f);
		defender.displayClientMessage(Component.translatable("message.swmod.guard_broken").withStyle(ChatFormatting.RED), true);
		attacker.displayClientMessage(Component.translatable("message.swmod.guard_broken_other").withStyle(ChatFormatting.GOLD), true);
		push(defender, attacker, 0.45);
	}

	private static void lockGuard(ServerPlayer player, SaberCombatState state, int ticks, int now) {
		state.guardLockedUntil = Math.max(state.guardLockedUntil, now + ticks);
		state.statusDirty = true;
		if (player.isUsingItem() && Lightsabers.isLightsaber(player.getUseItem())) {
			player.stopUsingItem();
		}
		ItemStack stack = player.getMainHandItem();
		if (Lightsabers.isLightsaber(stack)) {
			// Der Cooldown verhindert, dass der Client sofort wieder blockt (und zeigt ihn in der Hotbar an).
			player.getCooldowns().addCooldown(stack, ticks);
		}
	}

	static boolean isBlocking(Player player, SaberCombatState state, int now) {
		return now >= state.guardLockedUntil
				&& player.isUsingItem()
				&& Lightsabers.isReady(player.getUseItem(), player.level());
	}

	/** Liegt {@code pos} im Blockbereich vor dem Spieler? */
	static boolean isInFront(LivingEntity defender, Vec3 pos) {
		Vec3 look = defender.getLookAngle();
		Vec3 to = pos.subtract(defender.getEyePosition());
		double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
		double toLen = Math.sqrt(to.x * to.x + to.z * to.z);
		if (lookLen < 1.0E-4 || toLen < 1.0E-4) {
			return true;
		}
		double cos = (look.x * to.x + look.z * to.z) / (lookLen * toLen);
		return cos >= Math.cos(Math.toRadians(SaberTuning.BLOCK_HALF_ANGLE));
	}

	/** Stößt {@code target} von {@code source} weg. */
	static void push(LivingEntity target, LivingEntity source, double strength) {
		target.knockback(strength, source.getX() - target.getX(), source.getZ() - target.getZ());
		target.hurtMarked = true;
	}

	static void sparks(ServerLevel level, Vec3 point, int count, int colorA, int colorB) {
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, count, 0.05, 0.05, 0.05, 0.6);
		level.sendParticles(ParticleTypes.FIREWORK, point.x, point.y, point.z, count / 3, 0.02, 0.02, 0.02, 0.12);
		level.sendParticles(new DustParticleOptions(colorA, 1.2f), point.x, point.y, point.z, count / 2, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(new DustParticleOptions(colorB, 1.2f), point.x, point.y, point.z, count / 2, 0.1, 0.1, 0.1, 0.0);
	}
}
