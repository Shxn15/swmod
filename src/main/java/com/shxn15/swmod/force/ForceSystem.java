package com.shxn15.swmod.force;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.shxn15.swmod.ModItems;
import com.shxn15.swmod.combat.SaberCombatSystem;
import com.shxn15.swmod.network.ForceCastPayload;
import com.shxn15.swmod.network.ForceControlPayload;
import com.shxn15.swmod.network.ForceEventPayload;
import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.network.HolocronPayload;
import com.shxn15.swmod.network.ModNetworking;
import com.shxn15.swmod.registry.ModAttachments;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Das serverseitige Herz der Machtfähigkeiten: Machtvorrat, Abklingzeiten, Kanäle und das
 * Lernen am Holocron. Der Server ist die Autorität; Clients schicken nur Wünsche.
 */
public final class ForceSystem {
	private static final Map<UUID, ForceState> STATES = new HashMap<>();
	private static @Nullable MinecraftServer server;

	private ForceSystem() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ForceSystem::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
			ForceState state = STATES.get(handler.player.getUUID());
			if (state != null && state.channel != null) {
				stopChannel(handler.player, state, now(), false);
			}
			STATES.remove(handler.player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				reset(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reset(newPlayer));
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
			ForceState state = STATES.get(player.getUUID());
			if (state != null && state.channel != null) {
				stopChannel(player, state, now(), true);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
			STATES.clear();
			ForceImpacts.clear();
			server = null;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ForceCommand.register(dispatcher));
	}

	public static ForceState state(Player player) {
		return STATES.computeIfAbsent(player.getUUID(), uuid -> new ForceState());
	}

	/** Erlernte Fähigkeiten und Radbelegung (auch auf dem Client lesbar, wird synchronisiert). */
	public static ForceData data(Player player) {
		ForceData data = player.getAttached(ModAttachments.FORCE);
		return data != null ? data : ForceData.EMPTY;
	}

	public static void setData(ServerPlayer player, ForceData data) {
		player.setAttached(ModAttachments.FORCE, data);
		ForceState state = STATES.get(player.getUUID());
		if (state != null) {
			updateMaxForce(state, data);
		}
	}

	public static int now() {
		return server != null ? server.getTickCount() : 0;
	}

	public static float maxForce(ForceData data) {
		return ForceTuning.BASE_FORCE + ForceTuning.FORCE_PER_LEVEL * data.totalLevels();
	}

	// ------------------------------------------------------------------------------------------
	// Anfragen vom Client
	// ------------------------------------------------------------------------------------------

	public static void handleCast(ServerPlayer player, ForceCastPayload payload) {
		ForceAbility ability = ForceAbilities.REGISTRY.getValue(payload.ability());
		if (ability == null) {
			return;
		}

		ForceState state = state(player);
		int now = now();

		if (payload.action() == ForceCastPayload.STOP) {
			if (state.channel != null && state.channel.ability == ability) {
				stopChannel(player, state, now, false);
			}
			return;
		}

		ForceData data = data(player);
		int level = data.level(ability);
		if (level <= 0 || !player.isAlive() || player.isSpectator() || SaberCombatSystem.isBusy(player)) {
			return;
		}
		updateMaxForce(state, data);

		if (state.channel != null) {
			if (state.channel.ability == ability) {
				return;
			}
			stopChannel(player, state, now, false);
		}

		int cooldown = state.cooldownLeft(ability, now);
		if (cooldown > 0) {
			ModNetworking.sendForceEvent(player, ForceEventPayload.COOLDOWN, ability.id(), cooldown);
			return;
		}

		boolean free = player.hasInfiniteMaterials();
		float cost = ability.cost(level);
		if (!free && state.force < cost) {
			ModNetworking.sendForceEvent(player, ForceEventPayload.NO_FORCE, ability.id(), 0);
			return;
		}

		ForceContext context = new ForceContext(player, ability, level, state, now);
		if (ability.activation() == ForceAbility.Activation.INSTANT) {
			if (ability.cast(context)) {
				spend(state, free ? 0 : cost, now);
				startCooldown(player, state, ability, ability.cooldown(level), now);
			}
		} else {
			Object channelData = ability.startChannel(context);
			if (channelData != null) {
				spend(state, free ? 0 : cost, now);
				state.channel = new ForceState.Channel(ability, level, now, channelData);
			}
		}
	}

	public static void handleControl(ServerPlayer player, ForceControlPayload payload) {
		if (payload.action() == ForceControlPayload.SELECT) {
			int slot = (int) payload.value();
			ForceData data = data(player);
			if (slot != data.selected()) {
				setData(player, data.withSelected(slot));
			}
			ForceState state = STATES.get(player.getUUID());
			if (state != null && state.channel != null && data.withSelected(slot).selectedAbility() != state.channel.ability) {
				stopChannel(player, state, now(), false);
			}
			return;
		}

		ForceState state = STATES.get(player.getUUID());
		if (state == null || state.channel == null) {
			return;
		}
		ForceState.Channel channel = state.channel;
		ForceContext context = new ForceContext(player, channel.ability, channel.level, state, now());
		if (channel.ability.control(context, channel.data, payload.action(), payload.value())) {
			stopChannel(player, state, now(), false);
		}
	}

	public static void handleHolocron(ServerPlayer player, HolocronPayload payload) {
		if (!holdsHolocron(player)) {
			return;
		}

		ForceData data = data(player);
		ForceAbility ability = ForceAbilities.REGISTRY.getValue(payload.ability());

		switch (payload.action()) {
			case HolocronPayload.LEARN -> {
				if (ability == null) {
					return;
				}
				int level = data.level(ability);
				if (level >= ability.maxLevel()) {
					return;
				}
				int cost = ability.learnCost(level + 1);
				boolean free = player.hasInfiniteMaterials();
				if (!free && player.experienceLevel < cost) {
					return;
				}
				if (!free) {
					player.giveExperienceLevels(-cost);
				}

				ForceData learned = data.withLevel(ability.id(), level + 1);
				// Neu gelernte Fähigkeiten landen direkt im Rad, damit man sie sofort ausprobieren kann.
				if (level == 0 && learned.slotOf(ability.id()) < 0 && learned.firstFreeSlot() >= 0) {
					learned = learned.withSlot(learned.firstFreeSlot(), ability.id());
				}
				setData(player, learned);
				celebrate(player, ability, level + 1);
				ModNetworking.sendForceEvent(player, ForceEventPayload.LEARNED, ability.id(), level + 1);
			}
			case HolocronPayload.ASSIGN -> {
				if (ability != null && data.knows(ability)) {
					setData(player, data.withSlot(payload.slot(), ability.id()));
				}
			}
			case HolocronPayload.CLEAR -> setData(player, data.withSlot(payload.slot(), null));
			default -> {
			}
		}
	}

	public static boolean holdsHolocron(Player player) {
		return isHolocron(player.getMainHandItem()) || isHolocron(player.getOffhandItem());
	}

	private static boolean isHolocron(ItemStack stack) {
		return stack.is(ModItems.HOLOCRON);
	}

	/** Funkeln und Klang beim Lernen, auch für Umstehende sichtbar. */
	private static void celebrate(ServerPlayer player, ForceAbility ability, int newLevel) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
				newLevel == 1 ? ModSounds.HOLOCRON_LEARN : ModSounds.HOLOCRON_UPGRADE, SoundSource.PLAYERS, 1.0f, 1.0f);
		ModNetworking.broadcastForceFx(player, new ForceFxPayload(player.getId(), ability.id(), ForceFxPayload.LEARNED, newLevel, List.of(), List.of()));
	}

	// ------------------------------------------------------------------------------------------
	// Tick
	// ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer srv) {
		server = srv;
		int now = srv.getTickCount();

		for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
			ForceData data = data(player);
			ForceState state = STATES.get(player.getUUID());
			if (state == null) {
				if (data.levels().isEmpty()) continue;
				state = state(player);
			}
			updateMaxForce(state, data);

			for (ForceAbility ability : ForceAbilities.REGISTRY) {
				ability.tickPlayer(player, state, data.level(ability), now);
			}

			if (state.channel != null) {
				tickChannel(player, state, data, now);
			}

			if (state.channel == null && now - state.lastSpendTick > ForceTuning.FORCE_REGEN_DELAY && state.force < state.maxForce) {
				state.force = Math.min(state.maxForce, state.force + ForceTuning.FORCE_REGEN);
			}

			syncStatus(player, state, now);
		}

		ForceImpacts.tick(srv, now);
	}

	private static void tickChannel(ServerPlayer player, ForceState state, ForceData data, int now) {
		ForceState.Channel channel = state.channel;
		boolean free = player.hasInfiniteMaterials();
		float drain = free ? 0 : channel.ability.drain(channel.level);

		if (!player.isAlive() || player.isSpectator() || data.level(channel.ability) <= 0 || SaberCombatSystem.isBusy(player)
				|| now - channel.startTick > ForceTuning.MAX_CHANNEL_TICKS) {
			stopChannel(player, state, now, true);
			return;
		}
		if (state.force < drain) {
			stopChannel(player, state, now, false);
			ModNetworking.sendForceEvent(player, ForceEventPayload.NO_FORCE, channel.ability.id(), 0);
			return;
		}

		spend(state, drain, now);
		ForceContext context = new ForceContext(player, channel.ability, channel.level, state, now);
		if (!channel.ability.tickChannel(context, channel.data)) {
			stopChannel(player, state, now, true);
		}
	}

	/**
	 * Beendet den laufenden Kanal.
	 *
	 * @param notify dem Spieler mitteilen, dass der Server ihn beendet hat (sonst hält sein Client die Taste "ins Leere")
	 */
	static void stopChannel(ServerPlayer player, ForceState state, int now, boolean notify) {
		ForceState.Channel channel = state.channel;
		if (channel == null) {
			return;
		}
		state.channel = null;
		channel.ability.stopChannel(new ForceContext(player, channel.ability, channel.level, state, now), channel.data);
		startCooldown(player, state, channel.ability, channel.ability.cooldown(channel.level), now);
		if (notify) {
			ModNetworking.sendForceEvent(player, ForceEventPayload.CHANNEL_ENDED, channel.ability.id(), 0);
		}
	}

	/** Bricht alles ab (Tod, Wiederbelebung, Zurücksetzen): voller Vorrat, keine Kanäle, keine Wirkungen. */
	public static void reset(ServerPlayer player) {
		ForceState state = STATES.get(player.getUUID());
		if (state == null) {
			return;
		}
		if (state.channel != null) {
			stopChannel(player, state, now(), true);
		}
		for (ForceAbility ability : ForceAbilities.REGISTRY) {
			ability.reset(player, state);
		}
		state.force = -1f;
		state.statusDirty = true;
	}

	/** Füllt den Machtvorrat auf (z.B. per Befehl). */
	public static void refill(ServerPlayer player) {
		ForceState state = state(player);
		updateMaxForce(state, data(player));
		state.force = state.maxForce;
		state.cooldownUntil.clear();
		state.statusDirty = true;
	}

	private static void spend(ForceState state, float amount, int now) {
		if (amount > 0) {
			state.force = Math.max(0f, state.force - amount);
			state.lastSpendTick = now;
		}
	}

	private static void startCooldown(ServerPlayer player, ForceState state, ForceAbility ability, int ticks, int now) {
		if (ticks <= 0) {
			return;
		}
		state.cooldownUntil.put(ability.id(), now + ticks);
		ModNetworking.sendForceEvent(player, ForceEventPayload.COOLDOWN, ability.id(), ticks);
	}

	private static void updateMaxForce(ForceState state, ForceData data) {
		float max = maxForce(data);
		if (max != state.maxForce) {
			state.maxForce = max;
			state.statusDirty = true;
		}
		if (state.force < 0f) {
			state.force = max;
			state.statusDirty = true;
		}
		state.force = Math.min(state.force, state.maxForce);
	}

	private static void syncStatus(ServerPlayer player, ForceState state, int now) {
		boolean changed = Math.abs(state.force - state.sentForce) >= 1f
				|| (state.force >= state.maxForce && state.sentForce < state.maxForce)
				|| state.maxForce != state.sentMaxForce;
		if (state.statusDirty || changed || now - state.lastStatusSync > 40) {
			ModNetworking.sendForceStatus(player, state.force, state.maxForce);
			state.sentForce = state.force;
			state.sentMaxForce = state.maxForce;
			state.statusDirty = false;
			state.lastStatusSync = now;
		}
	}
}
