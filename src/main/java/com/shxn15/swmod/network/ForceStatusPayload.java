package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.shxn15.swmod.SwMod;

/**
 * Server -> eigener Client: Machtvorrat für das HUD.
 *
 * @param force    aktueller Vorrat
 * @param maxForce maximaler Vorrat
 */
public record ForceStatusPayload(float force, float maxForce) implements CustomPacketPayload {
	public static final Type<ForceStatusPayload> TYPE = new Type<>(SwMod.id("force_status"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForceStatusPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ForceStatusPayload::force,
			ByteBufCodecs.FLOAT, ForceStatusPayload::maxForce,
			ForceStatusPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
