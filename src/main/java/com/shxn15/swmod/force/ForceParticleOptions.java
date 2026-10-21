package com.shxn15.swmod.force;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.shxn15.swmod.registry.ModParticles;

/**
 * Leuchtende Machtpartikel in verschiedenen Formen. Sie leuchten selbst (additiv, volle Helligkeit),
 * deshalb wirken sie auch nachts wie kleine Lichtquellen.
 *
 * <p>Die drei Geschwindigkeitswerte beim Erzeugen sind die Bewegung; bei {@link #RING} sind sie
 * stattdessen die Blickrichtung des Rings (Normale) mal Wachstum pro Tick.
 *
 * @param style    Form, siehe Konstanten
 * @param color    RGB
 * @param size     Größe in Blöcken
 * @param lifetime Lebensdauer in Ticks
 */
public record ForceParticleOptions(int style, int color, float size, int lifetime) implements ParticleOptions {
	/** Vierzackiger Stern, funkelt (LEGO-Star-Wars-Glitzern). */
	public static final int SPARKLE = 0;
	/** Weicher Lichtpunkt, steigt sanft auf. */
	public static final int MOTE = 1;
	/** Heller Funke mit Schwerkraft. */
	public static final int SPARK = 2;
	/** Ausdehnender Ring (Druckwelle), ausgerichtet nach der Bewegungsrichtung. */
	public static final int RING = 3;
	/** Langgezogener Lichtstreifen in Bewegungsrichtung. */
	public static final int STREAK = 4;
	/** Dunkler, rauchiger Schleier (dunkle Seite). */
	public static final int SMOKE = 5;
	/** Kurzer, großer Lichtblitz. */
	public static final int FLASH = 6;
	/** Wie {@link #RING}, zieht sich aber zusammen (Sog). */
	public static final int RING_IN = 7;

	public static final int STYLE_COUNT = 8;

	public static final MapCodec<ForceParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.intRange(0, STYLE_COUNT - 1).fieldOf("style").forGetter(ForceParticleOptions::style),
			Codec.INT.fieldOf("color").forGetter(ForceParticleOptions::color),
			Codec.floatRange(0.01f, 16f).fieldOf("size").forGetter(ForceParticleOptions::size),
			Codec.intRange(1, 400).fieldOf("lifetime").forGetter(ForceParticleOptions::lifetime)
	).apply(instance, ForceParticleOptions::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, ForceParticleOptions> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ForceParticleOptions::style,
			ByteBufCodecs.INT, ForceParticleOptions::color,
			ByteBufCodecs.FLOAT, ForceParticleOptions::size,
			ByteBufCodecs.VAR_INT, ForceParticleOptions::lifetime,
			ForceParticleOptions::new);

	public static ForceParticleOptions of(int style, int color, float size, int lifetime) {
		return new ForceParticleOptions(style, color, size, lifetime);
	}

	@Override
	public ParticleType<ForceParticleOptions> getType() {
		return ModParticles.FORCE;
	}
}
