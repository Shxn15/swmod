package com.shxn15.swmod.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import com.shxn15.swmod.SwMod;

/**
 * Server -> Clients in der Nähe: visuelle Effekte einer Machtfähigkeit.
 *
 * @param caster  wer die Fähigkeit wirkt
 * @param ability welche Fähigkeit
 * @param kind    siehe Konstanten
 * @param level   Stufe der Fähigkeit (für stärkere Effekte)
 * @param targets betroffene Wesen
 * @param extra   fähigkeitsabhängige Zusatzdaten (z.B. Blitzketten als Paare, Dauer)
 */
public record ForceFxPayload(int caster, Identifier ability, int kind, int level, List<Integer> targets, List<Integer> extra)
		implements CustomPacketPayload {
	/** Eine Sofort-Fähigkeit wurde gewirkt. */
	public static final int CAST = 0;
	/** Ein Kanal beginnt. */
	public static final int CHANNEL_START = 1;
	/** Ziele eines laufenden Kanals haben sich geändert. */
	public static final int CHANNEL_UPDATE = 2;
	/** Ein Kanal endet. */
	public static final int CHANNEL_END = 3;
	/** Ein Aufprall (z.B. an einer Wand), Ziel ist das aufprallende Wesen. */
	public static final int IMPACT = 4;
	/** Eine zeitlich begrenzte Wirkung endet (z.B. Machtgeschwindigkeit). */
	public static final int EFFECT_END = 5;
	/** Die Fähigkeit wurde gelernt oder verbessert ({@code level} = neue Stufe). */
	public static final int LEARNED = 6;

	public static final Type<ForceFxPayload> TYPE = new Type<>(SwMod.id("force_fx"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ForceFxPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForceFxPayload::caster,
			Identifier.STREAM_CODEC, ForceFxPayload::ability,
			ByteBufCodecs.VAR_INT, ForceFxPayload::kind,
			ByteBufCodecs.VAR_INT, ForceFxPayload::level,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), ForceFxPayload::targets,
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), ForceFxPayload::extra,
			ForceFxPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
