package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.shxn15.swmod.SwMod;

/**
 * Client -> Server: der Spieler möchte etwas mit seinem Lichtschwert tun.
 *
 * @param action    siehe {@link #SWING}, {@link #TOGGLE}, {@link #THROW}
 * @param direction bei SWING die {@link com.shxn15.swmod.combat.SwingDirection} als ID
 */
public record SaberActionPayload(int action, int direction) implements CustomPacketPayload {
	public static final int SWING = 0;
	public static final int TOGGLE = 1;
	public static final int THROW = 2;

	public static final Type<SaberActionPayload> TYPE = new Type<>(SwMod.id("saber_action"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SaberActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SaberActionPayload::action,
			ByteBufCodecs.VAR_INT, SaberActionPayload::direction,
			SaberActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
