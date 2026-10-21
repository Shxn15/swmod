package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.combat.SaberAnimation;

/**
 * Server -> Client: ein Wesen führt eine Lichtschwert-Animation aus.
 *
 * @param entityId  wer animiert wird
 * @param animation {@link SaberAnimation} als ID
 * @param direction Schlagrichtung (falls relevant)
 * @param ticks     Ausholzeit (SWING) bzw. Dauer (Reaktionen)
 */
public record SaberAnimPayload(int entityId, int animation, int direction, int ticks) implements CustomPacketPayload {
	public static final Type<SaberAnimPayload> TYPE = new Type<>(SwMod.id("saber_anim"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SaberAnimPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SaberAnimPayload::entityId,
			ByteBufCodecs.VAR_INT, SaberAnimPayload::animation,
			ByteBufCodecs.VAR_INT, SaberAnimPayload::direction,
			ByteBufCodecs.VAR_INT, SaberAnimPayload::ticks,
			SaberAnimPayload::new);

	public SaberAnimation anim() {
		return SaberAnimation.byId(this.animation);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
