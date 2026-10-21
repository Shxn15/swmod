package com.shxn15.swmod.lightsaber;

import org.jspecify.annotations.Nullable;

/**
 * Vordefinierte Klingenfarben. Gespeichert wird am Item nur der RGB-Wert,
 * damit später beliebige Kristallfarben möglich sind.
 */
public enum BladeColor {
	BLUE("blue", 0x2E7BFF),
	GREEN("green", 0x2EFF4A),
	RED("red", 0xFF1E1E),
	PURPLE("purple", 0xB43CFF),
	YELLOW("yellow", 0xFFD21E),
	ORANGE("orange", 0xFF7A12),
	CYAN("cyan", 0x1EF0FF),
	WHITE("white", 0xEAF4FF);

	public final String id;
	public final int rgb;

	BladeColor(String id, int rgb) {
		this.id = id;
		this.rgb = rgb;
	}

	public String translationKey() {
		return "color.swmod." + this.id;
	}

	public static @Nullable BladeColor byRgb(int rgb) {
		for (BladeColor color : values()) {
			if (color.rgb == rgb) {
				return color;
			}
		}
		return null;
	}
}
