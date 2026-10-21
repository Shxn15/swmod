package com.shxn15.swmod.force;

/** Seite der Macht. Bestimmt die Farben von Leuchten, Auren und Benutzeroberfläche. */
public enum ForceSide {
	/** Jedi: blaues Leuchten wie in LEGO Star Wars. */
	LIGHT(0x4FA8FF, 0xC8ECFF),
	/** Sith: rotes Leuchten. */
	DARK(0xFF2B2B, 0xFFB0A0);

	/** Hauptfarbe (Aura, Rand des Fähigkeitssymbols). */
	public final int color;
	/** Helle Akzentfarbe (Kerne, Funken). */
	public final int accent;

	ForceSide(int color, int accent) {
		this.color = color;
		this.accent = accent;
	}
}
