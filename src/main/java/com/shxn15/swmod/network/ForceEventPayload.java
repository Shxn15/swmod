package com.shxn15.swmod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.shxn15.swmod.SwMod;

/**
 * Server -> eigener Client: Rückmeldung zu einer Fähigkeit.
 *
 * @param event   siehe Konstanten
 * @param ability betroffene Fähigkeit
 * @param value   bei {@link #COOLDOWN} die Abklingzeit in Ticks
 */
public record ForceEventPayload(int event, Identifier ability, int value) implements CustomPacketPayload {
	/** Abklingzeit hat begonnen. */
	public static final int COOLDOWN = 0;
	/** Zu wenig Macht. */
	public static final int NO_FORCE = 1;
	/** Kein Ziel im Visier. */
	public static final int NO_TARGET = 2;
	/** Das Ziel ist zu groß für die aktuelle Stufe. */
	public static final int TOO_HEAVY = 3;
	/** Der Server hat den Kanal beendet (z.B. Macht leer, Ziel verloren). */
	public static final int CHANNEL_ENDED = 4;
	/** Nichts zu heilen. */
	public static final int NOTHING_TO_HEAL = 5;
	/** Eine Fähigkeit wurde gelernt oder verbessert (value = neue Stufe). */
	public static final int LEARNED = 6;

	public static final Type<ForceEventPayload> TYPE = new Type<>(SwMod.id("force_event"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForceEventPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForceEventPayload::event,
			Identifier.STREAM_CODEC, ForceEventPayload::ability,
			ByteBufCodecs.VAR_INT, ForceEventPayload::value,
			ForceEventPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
