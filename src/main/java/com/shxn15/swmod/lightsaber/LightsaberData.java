package com.shxn15.swmod.lightsaber;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Zustand eines Lichtschwerts, gespeichert als Datenkomponente am Item.
 *
 * @param ignited     Klinge an/aus
 * @param color       Klingenfarbe als RGB
 * @param thrownUntil Spielzeit (Ticks), bis zu der das Schwert als "geworfen" gilt (0 = in der Hand)
 * @param throwId     Kennung des aktuellen Wurfs, damit das zurückkehrende Schwert das richtige Item findet
 */
public record LightsaberData(boolean ignited, int color, long thrownUntil, int throwId) {
	public static final LightsaberData DEFAULT = new LightsaberData(false, BladeColor.BLUE.rgb, 0L, 0);

	public static final Codec<LightsaberData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("ignited", false).forGetter(LightsaberData::ignited),
			Codec.INT.optionalFieldOf("color", BladeColor.BLUE.rgb).forGetter(LightsaberData::color),
			Codec.LONG.optionalFieldOf("thrown_until", 0L).forGetter(LightsaberData::thrownUntil),
			Codec.INT.optionalFieldOf("throw_id", 0).forGetter(LightsaberData::throwId)
	).apply(instance, LightsaberData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, LightsaberData> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, LightsaberData::ignited,
			ByteBufCodecs.INT, LightsaberData::color,
			ByteBufCodecs.VAR_LONG, LightsaberData::thrownUntil,
			ByteBufCodecs.VAR_INT, LightsaberData::throwId,
			LightsaberData::new);

	public static LightsaberData ofColor(int color) {
		return new LightsaberData(false, color, 0L, 0);
	}

	public boolean isThrown(long gameTime) {
		return this.thrownUntil > gameTime;
	}

	public LightsaberData withIgnited(boolean value) {
		return new LightsaberData(value, this.color, this.thrownUntil, this.throwId);
	}

	public LightsaberData withThrow(long until, int id) {
		return new LightsaberData(this.ignited, this.color, until, id);
	}

	public LightsaberData caught() {
		return new LightsaberData(this.ignited, this.color, 0L, 0);
	}
}
