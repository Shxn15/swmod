package com.shxn15.swmod.combat;

import org.jspecify.annotations.Nullable;

/**
 * Ein einzelner Angriff: Ausholen (windup) -> Treffphase (active) -> Erholung (recovery).
 * Wird identisch auf Server (Treffer) und Client (Animation) ausgewertet.
 */
public record SaberMove(SwingDirection direction, int windup, int active, int recovery) {
	/**
	 * @param previous  Richtung des vorherigen Schlags, falls dieser Schlag eine Kombo fortsetzt
	 * @param riposte   true direkt nach einer perfekten Parade (schnellerer Konter)
	 */
	public static SaberMove create(SwingDirection direction, @Nullable SwingDirection previous, boolean riposte) {
		int bonus = riposte ? 1 : 0;

		if (direction.isStab()) {
			return new SaberMove(direction, Math.max(1, SaberTuning.STAB_WINDUP_TICKS - bonus),
					SaberTuning.STAB_ACTIVE_TICKS, SaberTuning.STAB_RECOVERY_TICKS);
		}

		int windup = SaberTuning.WINDUP_TICKS;
		// Fließende Kombo: die Klinge ist schon auf der Startseite des neuen Schlags.
		if (previous != null && !previous.isStab() && direction.dot(previous) < -0.3f) {
			windup = SaberTuning.CHAIN_WINDUP_TICKS;
		}

		return new SaberMove(direction, Math.max(1, windup - bonus), SaberTuning.ACTIVE_TICKS, SaberTuning.RECOVERY_TICKS);
	}

	public int total() {
		return this.windup + this.active + this.recovery;
	}

	public int activeStart() {
		return this.windup;
	}

	public int recoveryStart() {
		return this.windup + this.active;
	}
}
