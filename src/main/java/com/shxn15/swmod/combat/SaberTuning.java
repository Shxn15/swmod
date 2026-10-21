package com.shxn15.swmod.combat;

/**
 * Alle Balance-Werte des Lichtschwertkampfs an einem Ort. Zeiten in Ticks (20 Ticks = 1 Sekunde).
 */
public final class SaberTuning {
	private SaberTuning() {
	}

	// --- Schaden & Reichweite ---
	public static final float DAMAGE = 10.0f;
	public static final float THROW_DAMAGE = 10.0f;
	/** Zusätzliche "Dicke" der Klinge bei der Trefferprüfung (Blöcke). */
	public static final float HIT_TOLERANCE = 0.15f;
	public static final float KNOCKBACK = 0.35f;

	// --- Schläge ---
	public static final int WINDUP_TICKS = 4;
	/** Ausholzeit, wenn ein Schlag flüssig aus dem vorherigen hervorgeht (z.B. links->rechts, dann rechts->links). */
	public static final int CHAIN_WINDUP_TICKS = 2;
	public static final int ACTIVE_TICKS = 5;
	public static final int RECOVERY_TICKS = 6;
	/** Ab diesem Tick der Erholungsphase darf der nächste Schlag beginnen (Kombo). */
	public static final int COMBO_DELAY_TICKS = 1;

	public static final int STAB_WINDUP_TICKS = 3;
	public static final int STAB_ACTIVE_TICKS = 3;
	public static final int STAB_RECOVERY_TICKS = 6;

	// --- Blocken & Parieren ---
	/** Wer so viele Ticks vor dem Treffer mit Rechtsklick blockt, pariert perfekt. */
	public static final int PARRY_WINDOW_TICKS = 6;
	/** Halber Öffnungswinkel vor dem Spieler, in dem geblockt werden kann (Grad). */
	public static final float BLOCK_HALF_ANGLE = 80f;
	public static final float MAX_BLOCK_POINTS = 100f;
	public static final float BLOCK_COST_SABER = 25f;
	public static final float BLOCK_COST_MELEE = 12f;
	public static final float BLOCK_COST_PROJECTILE = 6f;
	public static final float PARRY_REWARD = 10f;
	/** Regeneration pro Tick, wenn man gerade nicht blockt. */
	public static final float BLOCK_REGEN = 0.6f;
	/** Regeneration pro Tick, während man blockt. */
	public static final float BLOCK_REGEN_WHILE_BLOCKING = 0.1f;
	/** So lange nach einem geblockten Treffer regeneriert nichts. */
	public static final int BLOCK_REGEN_DELAY = 30;

	/** Angreifer nach perfekter Parade: kann weder schlagen noch blocken. */
	public static final int PARRY_STAGGER_TICKS = 18;
	/** Angreifer nach normalem Block: kurzes Zurückprallen. */
	public static final int DEFLECT_RECOIL_TICKS = 8;
	/** Beide Kämpfer nach einem Klingenzusammenstoß. */
	public static final int CLASH_RECOIL_TICKS = 8;
	/** Verteidiger, dessen Deckung gebrochen wurde. */
	public static final int GUARD_BREAK_STUN_TICKS = 30;
	/** Nach einer perfekten Parade ist der nächste eigene Schlag schneller. */
	public static final int RIPOSTE_WINDOW_TICKS = 16;
	/** Abstand, ab dem sich zwei Klingen berühren (Blöcke). */
	public static final float CLASH_DISTANCE = 0.2f;

	// --- Wurf ---
	public static final double THROW_SPEED = 1.45;
	public static final double THROW_RETURN_SPEED = 1.7;
	public static final int THROW_OUTBOUND_TICKS = 12;
	public static final int THROW_MAX_TICKS = 100;
	/** Sicherheitsnetz: so lange gilt das Schwert maximal als "geworfen". */
	public static final int THROW_LOCK_TICKS = THROW_MAX_TICKS + 20;
	public static final int THROW_COOLDOWN_TICKS = 10;
	/** Radius der rotierenden Klinge beim Wurf. */
	public static final float THROW_HIT_RADIUS = 0.85f;
}
