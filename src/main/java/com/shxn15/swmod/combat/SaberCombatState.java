package com.shxn15.swmod.combat;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.Vec3;

/** Serverseitiger Kampfzustand eines Spielers. */
public final class SaberCombatState {
	// --- laufender Angriff ---
	@Nullable SaberMove move;
	int moveTick;
	@Nullable SwingDirection lastDirection;
	@Nullable SwingDirection queued;
	final IntSet hitThisMove = new IntOpenHashSet();
	/** Klingensegmente dieses Ticks (je Unterschritt: Emitter, Spitze). */
	Vec3[][] segments;
	boolean interrupted;

	// --- Verteidigung ---
	float blockPoints = SaberTuning.MAX_BLOCK_POINTS;
	int lastBlockHitTick = -1000;

	// --- Sperren (Server-Tick, bis zu dem etwas nicht geht) ---
	int stunnedUntil;
	int guardLockedUntil;
	int riposteUntil;
	int throwCooldownUntil;

	// --- HUD-Synchronisation ---
	float sentBlockPoints = -1;
	boolean statusDirty = true;
	int lastStatusSync;

	boolean isAttacking() {
		return this.move != null;
	}

	/** Ausholen oder Treffphase: der Spieler ist "festgelegt" und kann nicht blocken. */
	boolean isCommitted() {
		return this.move != null && this.moveTick < this.move.recoveryStart();
	}

	boolean isInActivePhase() {
		return this.move != null && this.moveTick >= this.move.activeStart() && this.moveTick < this.move.recoveryStart();
	}

	boolean canChainNow() {
		return this.move == null || this.moveTick >= this.move.recoveryStart() + SaberTuning.COMBO_DELAY_TICKS;
	}

	void cancelMove() {
		this.move = null;
		this.queued = null;
		this.segments = null;
		this.hitThisMove.clear();
	}

	void drainBlockPoints(float amount, int now) {
		this.blockPoints = Math.max(0, this.blockPoints - amount);
		this.lastBlockHitTick = now;
		this.statusDirty = true;
	}

	void addBlockPoints(float amount) {
		this.blockPoints = Math.min(SaberTuning.MAX_BLOCK_POINTS, this.blockPoints + amount);
		this.statusDirty = true;
	}

	public float blockPoints() {
		return this.blockPoints;
	}
}
