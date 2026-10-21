package com.shxn15.swmod.force;

import java.util.Locale;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;

import net.minecraft.network.chat.Component;

/**
 * Ein Wert einer Fähigkeit, der sich mit der Stufe ändert (für die Holocron-Ansicht:
 * "Reichweite: 10 → 13 Blöcke").
 *
 * @param key   Übersetzungsschlüssel-Suffix: {@code force.swmod.stat.<key>}
 * @param value Wert auf der jeweiligen Stufe (1 = erste Stufe)
 */
public record ForceStat(String key, IntFunction<Component> value) {
	public Component label() {
		return Component.translatable("force.swmod.stat." + this.key);
	}

	public static ForceStat blocks(String key, IntToDoubleFunction value) {
		return unit(key, "blocks", value);
	}

	public static ForceStat seconds(String key, IntToDoubleFunction ticks) {
		return unit(key, "seconds", level -> ticks.applyAsDouble(level) / 20.0);
	}

	public static ForceStat hearts(String key, IntToDoubleFunction health) {
		return unit(key, "hearts", level -> health.applyAsDouble(level) / 2.0);
	}

	public static ForceStat percent(String key, IntToDoubleFunction fraction) {
		return unit(key, "percent", level -> fraction.applyAsDouble(level) * 100.0);
	}

	public static ForceStat degrees(String key, IntToDoubleFunction value) {
		return unit(key, "degrees", value);
	}

	public static ForceStat number(String key, IntToDoubleFunction value) {
		return new ForceStat(key, level -> Component.literal(format(value.applyAsDouble(level))));
	}

	/** Ein Wert, der nur ja/nein ist (z.B. "Entwaffnet Gegner"). */
	public static ForceStat flag(String key, IntPredicate enabled) {
		return new ForceStat(key, level -> Component.translatable(enabled.test(level) ? "force.swmod.yes" : "force.swmod.no"));
	}

	private static ForceStat unit(String key, String unit, IntToDoubleFunction value) {
		return new ForceStat(key, level -> Component.translatable("force.swmod.unit." + unit, format(value.applyAsDouble(level))));
	}

	public static String format(double value) {
		if (Math.abs(value - Math.round(value)) < 0.05) {
			return Long.toString(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
