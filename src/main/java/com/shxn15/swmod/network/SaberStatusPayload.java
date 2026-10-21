package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.shxn15.swmod.SwMod;

/**
 * Server -> eigener Client: Werte für das Kampf-HUD.
 *
 * @param blockPoints aktuelle Blockpunkte (0..100)
 * @param stunTicks   so lange kann der Spieler nicht angreifen
 * @param guardTicks  so lange kann der Spieler nicht blocken
 */
public record SaberStatusPayload(float blockPoints, int stunTicks, int guardTicks) implements CustomPacketPayload {
	public static final Type<SaberStatusPayload> TYPE = new Type<>(SwMod.id("saber_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SaberStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, SaberStatusPayload::blockPoints,
			ByteBufCodecs.VAR_INT, SaberStatusPayload::stunTicks,
			ByteBufCodecs.VAR_INT, SaberStatusPayload::guardTicks,
			SaberStatusPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
