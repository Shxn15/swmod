package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.shxn15.swmod.SwMod;

/**
 * Client -> Server: Steuerung während eines Kanals.
 *
 * @param action {@link #SELECT} (Radplatz wählen, value = Platz oder -1), {@link #DISTANCE}
 *               (Mausrad beim Machtgriff, value = Änderung in Blöcken), {@link #HURL} (Linksklick: wegschleudern)
 */
public record ForceControlPayload(int action, float value) implements CustomPacketPayload {
	public static final int SELECT = 0;
	public static final int DISTANCE = 1;
	public static final int HURL = 2;

	public static final Type<ForceControlPayload> TYPE = new Type<>(SwMod.id("force_control"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForceControlPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForceControlPayload::action,
			ByteBufCodecs.FLOAT, ForceControlPayload::value,
			ForceControlPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
