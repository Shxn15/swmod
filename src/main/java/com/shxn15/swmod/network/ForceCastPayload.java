package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.shxn15.swmod.SwMod;

/**
 * Client -> Server: Rechtsklick mit gewählter Machtfähigkeit.
 *
 * @param action  {@link #START} beim Drücken, {@link #STOP} beim Loslassen (Kanal-Fähigkeiten)
 * @param ability die gewählte Fähigkeit
 */
public record ForceCastPayload(int action, Identifier ability) implements CustomPacketPayload {
	public static final int START = 0;
	public static final int STOP = 1;

	public static final Type<ForceCastPayload> TYPE = new Type<>(SwMod.id("force_cast"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForceCastPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForceCastPayload::action,
			Identifier.STREAM_CODEC, ForceCastPayload::ability,
			ForceCastPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
