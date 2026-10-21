package com.shxn15.swmod.combat;

import net.minecraft.util.Mth;

/**
 * Die Richtung eines Lichtschwertschlags, so wie sie der Spieler mit der Maus "zeichnet".
 * {@code screenX}/{@code screenY} beschreiben die Bewegung der Klinge auf dem Bildschirm
 * (x = nach rechts, y = nach oben). {@link #STAB} ist der Stoß nach vorne ohne Richtung.
 */
public enum SwingDirection {
	RIGHT(1, 0),          // links -> rechts
	UP_RIGHT(1, 1),       // unten links -> oben rechts
	UP(0, 1),             // unten -> oben
	UP_LEFT(-1, 1),       // unten rechts -> oben links
	LEFT(-1, 0),          // rechts -> links
	DOWN_LEFT(-1, -1),    // oben rechts -> unten links
	DOWN(0, -1),          // oben -> unten (Überkopfschlag)
	DOWN_RIGHT(1, -1),    // oben links -> unten rechts
	STAB(0, 0);           // Stoß nach vorne

	private static final SwingDirection[] VALUES = values();

	public final float screenX;
	public final float screenY;

	SwingDirection(float x, float y) {
		float len = Mth.sqrt(x * x + y * y);
		this.screenX = len == 0 ? 0 : x / len;
		this.screenY = len == 0 ? 0 : y / len;
	}

	public boolean isStab() {
		return this == STAB;
	}

	/** Liefert die Richtung, die der Mausbewegung (dx nach rechts, dy nach oben) am nächsten kommt. */
	public static SwingDirection fromScreenVector(double dx, double dy) {
		double angle = Math.toDegrees(Math.atan2(dy, dx));
		int sector = Math.floorMod((int) Math.round(angle / 45.0), 8);
		return VALUES[sector];
	}

	/** Wie stark zeigen zwei Schlagrichtungen in dieselbe Richtung (-1 = entgegengesetzt, 1 = gleich). */
	public float dot(SwingDirection other) {
		return this.screenX * other.screenX + this.screenY * other.screenY;
	}

	public int id() {
		return this.ordinal();
	}

	public static SwingDirection byId(int id) {
		return id >= 0 && id < VALUES.length ? VALUES[id] : STAB;
	}
}
