package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.shxn15.swmod.SwMod;

/**
 * Client -> Server: Aktion im Holocron.
 *
 * @param action  {@link #LEARN} (nächste Stufe lernen), {@link #ASSIGN} (auf Radplatz legen), {@link #CLEAR} (Radplatz leeren)
 * @param ability betroffene Fähigkeit (bei CLEAR egal)
 * @param slot    Radplatz (bei LEARN egal)
 */
public record HolocronPayload(int action, Identifier ability, int slot) implements CustomPacketPayload {
	public static final int LEARN = 0;
	public static final int ASSIGN = 1;
	public static final int CLEAR = 2;

	public static final Type<HolocronPayload> TYPE = new Type<>(SwMod.id("holocron"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HolocronPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HolocronPayload::action,
			Identifier.STREAM_CODEC, HolocronPayload::ability,
			ByteBufCodecs.VAR_INT, HolocronPayload::slot,
			HolocronPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
