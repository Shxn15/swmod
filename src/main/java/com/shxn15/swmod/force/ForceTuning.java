package com.shxn15.swmod.force;

/**
 * Alle Balance-Werte der Machtfähigkeiten an einem Ort. Zeiten in Ticks (20 Ticks = 1 Sekunde),
 * Leben in halben Herzen. Arrays enthalten einen Wert je Stufe (Stufe 1, 2, 3).
 */
public final class ForceTuning {
	private ForceTuning() {
	}

	/** Wert für eine Stufe (1-basiert) aus einem Stufen-Array. */
	public static float at(float[] values, int level) {
		return values[Math.clamp(level - 1, 0, values.length - 1)];
	}

	public static int at(int[] values, int level) {
		return values[Math.clamp(level - 1, 0, values.length - 1)];
	}

	// --- Machtvorrat ---
	public static final float BASE_FORCE = 100f;
	/** Jede erlernte Stufe vergrößert den Vorrat. */
	public static final float FORCE_PER_LEVEL = 4f;
	/** Regeneration pro Tick. */
	public static final float FORCE_REGEN = 0.5f;
	/** So lange nach dem letzten Verbrauch regeneriert nichts. */
	public static final int FORCE_REGEN_DELAY = 25;
	/** Kanal-Fähigkeiten enden spätestens nach dieser Zeit. */
	public static final int MAX_CHANNEL_TICKS = 20 * 30;

	// --- Machtstoß ---
	public static final int[] PUSH_LEARN = {2, 5, 8};
	public static final float[] PUSH_COST = {25, 22, 20};
	public static final int[] PUSH_COOLDOWN = {20, 16, 12};
	public static final float[] PUSH_RANGE = {8, 10, 13};
	public static final float[] PUSH_ANGLE = {32, 40, 48};
	/** Abfluggeschwindigkeit (Blöcke/Tick); ein Zombie fliegt damit etwa 6 / 9 / 13 Blöcke weit. */
	public static final float[] PUSH_STRENGTH = {1.0f, 1.3f, 1.7f};
	public static final float[] PUSH_LIFT = {0.42f, 0.48f, 0.55f};
	public static final float[] PUSH_DAMAGE = {1, 2, 3};

	// --- Machtzug ---
	public static final int[] PULL_LEARN = {2, 5, 8};
	public static final float[] PULL_COST = {22, 20, 18};
	public static final int[] PULL_COOLDOWN = {20, 16, 12};
	public static final float[] PULL_RANGE = {10, 13, 16};
	public static final float[] PULL_ANGLE = {28, 34, 40};
	public static final float[] PULL_STRENGTH = {1.3f, 1.65f, 2.0f};
	/** Ab dieser Stufe reißt der Zug Gegnern die Waffe aus der Hand. */
	public static final int PULL_DISARM_LEVEL = 3;

	// --- Machtgriff (Telekinese) ---
	public static final int[] GRAB_LEARN = {3, 6, 10};
	public static final float[] GRAB_COST = {10, 10, 10};
	public static final float[] GRAB_DRAIN = {0.55f, 0.45f, 0.35f};
	public static final int[] GRAB_COOLDOWN = {20, 15, 10};
	public static final float[] GRAB_RANGE = {10, 14, 18};
	/** Größte Breite bzw. Höhe (Blöcke), die noch gegriffen werden kann. */
	public static final float[] GRAB_MAX_WIDTH = {1.0f, 1.6f, 4.5f};
	public static final float[] GRAB_MAX_HEIGHT = {2.1f, 3.0f, 4.5f};
	/** Wurfgeschwindigkeit beim Linksklick und Höchstgeschwindigkeit beim Loslassen. */
	public static final float[] GRAB_THROW_SPEED = {2.0f, 2.6f, 3.2f};
	public static final float GRAB_MIN_DISTANCE = 2.2f;

	// --- Machtgeschwindigkeit ---
	public static final int[] SPEED_LEARN = {3, 6, 9};
	public static final float[] SPEED_COST = {30, 30, 30};
	public static final int[] SPEED_COOLDOWN = {320, 300, 280};
	public static final int[] SPEED_DURATION = {120, 180, 240};
	/** Zusätzliche Laufgeschwindigkeit (0,6 = +60 %). */
	public static final float[] SPEED_BONUS = {0.6f, 1.0f, 1.5f};
	public static final float[] SPEED_JUMP_BONUS = {0.1f, 0.15f, 0.2f};
	public static final float[] SPEED_STEP_BONUS = {0f, 0.6f, 0.6f};
	public static final float[] SPEED_SAFE_FALL = {0f, 2f, 5f};

	// --- Machtheilung ---
	public static final int[] HEAL_LEARN = {3, 6, 9};
	public static final float[] HEAL_COST = {40, 35, 30};
	public static final int[] HEAL_COOLDOWN = {240, 200, 160};
	public static final float[] HEAL_AMOUNT = {6, 9, 12};
	public static final int[] HEAL_REGEN_TICKS = {60, 80, 100};
	/** Ab dieser Stufe werden Gift und Wither geheilt. */
	public static final int HEAL_CLEANSE_LEVEL = 2;
	/** Ab dieser Stufe werden auch Verbündete in der Nähe geheilt (halbe Menge). */
	public static final int HEAL_ALLIES_LEVEL = 3;
	public static final float HEAL_ALLY_RADIUS = 6f;

	// --- Machtblitze ---
	public static final int[] LIGHTNING_LEARN = {4, 8, 12};
	public static final float[] LIGHTNING_COST = {8, 8, 8};
	public static final float[] LIGHTNING_DRAIN = {0.9f, 0.8f, 0.7f};
	public static final int[] LIGHTNING_COOLDOWN = {20, 15, 10};
	public static final float[] LIGHTNING_RANGE = {8, 10, 12};
	public static final float[] LIGHTNING_ANGLE = {22, 28, 34};
	public static final int[] LIGHTNING_TARGETS = {1, 3, 5};
	/** Stufe 3: Blitze springen auf weitere Gegner über. */
	public static final int[] LIGHTNING_CHAINS = {0, 0, 2};
	public static final float LIGHTNING_CHAIN_RANGE = 4.5f;
	public static final int LIGHTNING_HIT_INTERVAL = 4;
	public static final float[] LIGHTNING_DAMAGE = {1.0f, 1.5f, 2.0f};
	/** Blockpunkte, die das Abfangen eines Blitzes mit dem Lichtschwert kostet (pro Treffer). */
	public static final float LIGHTNING_BLOCK_COST = 3f;

	// --- Machtwürgen ---
	public static final int[] GRIP_LEARN = {4, 8, 12};
	public static final float[] GRIP_COST = {10, 10, 10};
	public static final float[] GRIP_DRAIN = {0.7f, 0.6f, 0.5f};
	public static final int[] GRIP_COOLDOWN = {30, 25, 20};
	public static final float[] GRIP_RANGE = {10, 13, 16};
	public static final float[] GRIP_LIFT = {1.0f, 1.4f, 1.8f};
	public static final int GRIP_HIT_INTERVAL = 10;
	public static final float[] GRIP_DAMAGE = {1.5f, 2.5f, 3.5f};
	public static final float[] GRIP_MAX_WIDTH = {1.0f, 1.6f, 4.5f};
	public static final float[] GRIP_MAX_HEIGHT = {2.1f, 3.0f, 4.5f};
	/** Ab dieser Stufe wird das Opfer beim Loslassen in Blickrichtung geschleudert. */
	public static final int GRIP_THROW_LEVEL = 3;
	public static final float GRIP_THROW_SPEED = 1.4f;

	// --- Aufprall (Wand, Boden) nach Stoß, Zug oder Wurf ---
	/** Ab dieser Geschwindigkeit (Blöcke/Tick) tut ein Aufprall weh. */
	public static final float IMPACT_MIN_SPEED = 0.55f;
	public static final float IMPACT_DAMAGE_PER_SPEED = 7f;
	public static final float IMPACT_MAX_DAMAGE = 14f;
	/** So lange nach einem Stoß zählt ein Aufprall. */
	public static final int IMPACT_TRACK_TICKS = 40;

	/** Zielhilfe (Grad) für Fähigkeiten mit einem einzelnen Ziel. */
	public static final float AIM_ASSIST_ANGLE = 7f;
}
