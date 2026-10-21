package com.shxn15.swmod.network;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.shxn15.swmod.combat.SaberAnimation;
import com.shxn15.swmod.combat.SaberCombatSystem;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.force.ForceSystem;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SaberActionPayload.TYPE, SaberActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SaberAnimPayload.TYPE, SaberAnimPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SaberStatusPayload.TYPE, SaberStatusPayload.CODEC);

		PayloadTypeRegistry.serverboundPlay().register(ForceCastPayload.TYPE, ForceCastPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ForceControlPayload.TYPE, ForceControlPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HolocronPayload.TYPE, HolocronPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceStatusPayload.TYPE, ForceStatusPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceEventPayload.TYPE, ForceEventPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ForceFxPayload.TYPE, ForceFxPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(ForceCastPayload.TYPE, (payload, context) -> ForceSystem.handleCast(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(ForceControlPayload.TYPE, (payload, context) -> ForceSystem.handleControl(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(HolocronPayload.TYPE, (payload, context) -> ForceSystem.handleHolocron(context.player(), payload));

		ServerPlayNetworking.registerGlobalReceiver(SaberActionPayload.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			switch (payload.action()) {
				case SaberActionPayload.SWING -> SaberCombatSystem.requestSwing(player, SwingDirection.byId(payload.direction()));
				case SaberActionPayload.TOGGLE -> SaberCombatSystem.toggle(player);
				case SaberActionPayload.THROW -> SaberCombatSystem.throwSaber(player);
				default -> {
				}
			}
		});
	}

	/**
	 * Schickt eine Animation an alle Spieler, die das Wesen sehen.
	 *
	 * @param includeSelf ob ein animierter Spieler das Paket selbst auch bekommt
	 *                    (nicht nötig, wenn sein Client die Animation schon vorhergesagt hat)
	 */
	public static void broadcastAnim(Entity entity, SaberAnimation animation, SwingDirection direction, int ticks, boolean includeSelf) {
		SaberAnimPayload payload = new SaberAnimPayload(entity.getId(), animation.id(), direction.id(), ticks);

		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (player != entity) {
				ServerPlayNetworking.send(player, payload);
			}
		}

		if (includeSelf && entity instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	public static void sendStatus(ServerPlayer player, float blockPoints, int stunTicks, int guardTicks) {
		ServerPlayNetworking.send(player, new SaberStatusPayload(blockPoints, stunTicks, guardTicks));
	}

	/** Schickt einen Macht-Effekt an alle Spieler, die das Wesen sehen, und an das Wesen selbst. */
	public static void broadcastForceFx(Entity entity, ForceFxPayload payload) {
		for (ServerPlayer player : PlayerLookup.tracking(entity)) {
			if (player != entity) {
				ServerPlayNetworking.send(player, payload);
			}
		}
		if (entity instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	public static void sendForceStatus(ServerPlayer player, float force, float maxForce) {
		ServerPlayNetworking.send(player, new ForceStatusPayload(force, maxForce));
	}

	public static void sendForceEvent(ServerPlayer player, int event, Identifier ability, int value) {
		ServerPlayNetworking.send(player, new ForceEventPayload(event, ability, value));
	}
}
