package com.shxn15.swmod.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.shxn15.swmod.combat.SaberAnimation;
import com.shxn15.swmod.combat.SaberCombatSystem;
import com.shxn15.swmod.combat.SwingDirection;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(SaberActionPayload.TYPE, SaberActionPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SaberAnimPayload.TYPE, SaberAnimPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SaberStatusPayload.TYPE, SaberStatusPayload.CODEC);

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
}
