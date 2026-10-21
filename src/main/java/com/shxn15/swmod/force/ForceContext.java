package com.shxn15.swmod.force;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import com.shxn15.swmod.network.ForceFxPayload;
import com.shxn15.swmod.network.ModNetworking;

/**
 * Alles, was eine Fähigkeit beim Wirken braucht.
 *
 * @param level Stufe der Fähigkeit (1..max)
 * @param now   aktueller Server-Tick
 */
public record ForceContext(ServerPlayer player, ForceAbility ability, int level, ForceState state, int now) {
	public ServerLevel world() {
		return this.player.level();
	}

	public Vec3 eye() {
		return this.player.getEyePosition();
	}

	public Vec3 look() {
		return this.player.getLookAngle();
	}

	/** Schickt einen visuellen Effekt an alle Spieler in der Nähe (inklusive Wirkendem). */
	public void fx(int kind, List<Integer> targets, List<Integer> extra) {
		ModNetworking.broadcastForceFx(this.player,
				new ForceFxPayload(this.player.getId(), this.ability.id(), kind, this.level, targets, extra));
	}

	public void fx(int kind, List<Integer> targets) {
		this.fx(kind, targets, List.of());
	}

	/** Rückmeldung nur an den Wirkenden (siehe {@link com.shxn15.swmod.network.ForceEventPayload}). */
	public void event(int event) {
		ModNetworking.sendForceEvent(this.player, event, this.ability.id(), 0);
	}

	public void playSound(SoundEvent sound, float volume, float pitch) {
		this.world().playSound(null, this.player.getX(), this.player.getEyeY(), this.player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}
}
