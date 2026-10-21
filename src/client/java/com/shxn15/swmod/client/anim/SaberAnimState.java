package com.shxn15.swmod.client.anim;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.combat.SaberAnimation;
import com.shxn15.swmod.combat.SaberGeometry;
import com.shxn15.swmod.combat.SaberMove;
import com.shxn15.swmod.combat.SaberPose;
import com.shxn15.swmod.combat.SwingDirection;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.registry.ModSounds;

/**
 * Clientseitiger Animationszustand eines Lichtschwertträgers. Alle Posen sind reine Funktionen
 * der Zeit, dadurch sind Animationen unabhängig von der Bildrate und Schweife lassen sich
 * einfach durch Abtasten der Vergangenheit zeichnen.
 */
public final class SaberAnimState {
	/** Grundhaltung in der Ego-Perspektive: etwas weiter rechts, damit der Griff im Bild ist. */
	public static final SaberPose FP_IDLE = SaberPose.of(0.30f, -0.33f, 0.50f, -0.28f, 0.90f, 0.30f);
	public static final SaberPose FP_BLOCK = SaberPose.of(0.30f, -0.22f, 0.46f, -1.0f, 0.24f, 0.06f);
	/** Grundhaltung für die Third-Person-Ansicht (Hände vor der Brust). */
	public static final SaberPose TP_IDLE = SaberPose.of(0.10f, -0.38f, 0.42f, -0.18f, 0.92f, 0.32f);
	public static final SaberPose TP_BLOCK = SaberPose.of(0.06f, -0.20f, 0.42f, -1.0f, 0.25f, 0.05f);

	private float extension;
	private float prevExtension;
	private float block;
	private float prevBlock;
	private boolean thrown;

	private @Nullable SaberAnimation action;
	private @Nullable SaberMove move;
	private SwingDirection direction = SwingDirection.STAB;
	private int actionStart;
	private int actionDuration;
	private SaberPose actionFromFp = FP_IDLE;
	private SaberPose actionFromTp = TP_IDLE;
	private boolean swingSoundPlayed;

	/** Für die Vorhersage des eigenen Spielers: nächster Schlag im Kombo-Puffer. */
	private @Nullable SwingDirection queued;

	// ------------------------------------------------------------------------------------------

	void tick(LivingEntity entity, ItemStack stack, int now) {
		this.prevExtension = this.extension;
		this.prevBlock = this.block;

		this.thrown = Lightsabers.isThrown(stack, entity.level());
		boolean lit = Lightsabers.isIgnited(stack) && !this.thrown;
		// Zünden geht schnell, Einfahren etwas langsamer.
		this.extension = approach(this.extension, lit ? 1f : 0f, lit ? 0.24f : 0.17f);

		boolean blocking = entity.isUsingItem() && Lightsabers.isLightsaber(entity.getUseItem());
		this.block = approach(this.block, blocking ? 1f : 0f, 0.34f);

		if (this.action == SaberAnimation.SWING && this.move != null) {
			int elapsed = now - this.actionStart;
			if (!this.swingSoundPlayed && elapsed >= this.move.windup()) {
				this.swingSoundPlayed = true;
				float pitch = 0.9f + entity.getRandom().nextFloat() * 0.2f;
				entity.level().playLocalSound(entity.getX(), entity.getEyeY(), entity.getZ(), ModSounds.LIGHTSABER_SWING,
						entity.getSoundSource(), 0.9f, pitch, false);
			}
		}

		if (this.action != null && now - this.actionStart >= this.actionDuration) {
			this.action = null;
			this.move = null;
		}

		if (!lit && this.action == SaberAnimation.SWING) {
			this.action = null;
			this.move = null;
		}

		if (this.queued != null && this.canChain(now)) {
			SwingDirection next = this.queued;
			this.queued = null;
			this.startSwing(next, now, -1);
		}
	}

	private static float approach(float value, float target, float step) {
		return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
	}

	// ------------------------------------------------------------------------------------------
	// Aktionen
	// ------------------------------------------------------------------------------------------

	/** Darf (aus Sicht der Animation) jetzt ein neuer Schlag beginnen? */
	public boolean canChain(int now) {
		if (this.action == null) return true;
		if (this.action == SaberAnimation.SWING && this.move != null) {
			return now - this.actionStart >= this.move.recoveryStart() + com.shxn15.swmod.combat.SaberTuning.COMBO_DELAY_TICKS;
		}
		// Reaktionen (Block-Treffer, Parade, Fangen) dürfen von einem Schlag unterbrochen werden.
		return this.action == SaberAnimation.BLOCK_HIT || this.action == SaberAnimation.PARRY || this.action == SaberAnimation.CATCH;
	}

	public boolean isCommitted(int now) {
		return this.action == SaberAnimation.SWING && this.move != null && now - this.actionStart < this.move.recoveryStart();
	}

	public void queue(SwingDirection direction) {
		this.queued = direction;
	}

	/**
	 * Startet einen Schlag.
	 *
	 * @param windupOverride Ausholzeit vom Server (oder -1, um sie lokal zu berechnen)
	 */
	public void startSwing(SwingDirection dir, int now, int windupOverride) {
		float time = now;
		SwingDirection previous = this.action == SaberAnimation.SWING && this.move != null ? this.move.direction() : null;
		SaberMove created = SaberMove.create(dir, previous, false);
		if (windupOverride > 0) {
			created = new SaberMove(dir, windupOverride, created.active(), created.recovery());
		}

		this.captureFrom(time);
		this.action = SaberAnimation.SWING;
		this.move = created;
		this.direction = dir;
		this.actionStart = now;
		this.actionDuration = created.total();
		this.swingSoundPlayed = false;
	}

	/** Startet eine Reaktion (Abprallen, Taumeln, Parade, ...). */
	public void startReaction(SaberAnimation animation, SwingDirection dir, int duration, int now) {
		if (animation == SaberAnimation.CANCEL) {
			this.action = null;
			this.move = null;
			this.queued = null;
			return;
		}

		// Für das Abprallen merken wir uns die Richtung des unterbrochenen Schlags.
		if (this.action == SaberAnimation.SWING && this.move != null) {
			dir = this.move.direction();
		}

		this.captureFrom(now);
		this.action = animation;
		this.move = null;
		this.direction = dir;
		this.actionStart = now;
		this.actionDuration = Math.max(2, duration);
		if (animation == SaberAnimation.STAGGER || animation == SaberAnimation.GUARD_BREAK || animation == SaberAnimation.RECOIL
				|| animation == SaberAnimation.CLASH) {
			this.queued = null;
		}
	}

	private void captureFrom(float time) {
		this.actionFromFp = this.pose(time, true);
		this.actionFromTp = this.pose(time, false);
	}

	// ------------------------------------------------------------------------------------------
	// Posen
	// ------------------------------------------------------------------------------------------

	/**
	 * Die Pose des Schwerts zum Zeitpunkt {@code time} (Client-Ticks inkl. Teil-Tick).
	 *
	 * @param firstPerson Ego-Perspektive (leicht andere Ruhehaltung, damit der Griff im Bild ist)
	 */
	public SaberPose pose(float time, boolean firstPerson) {
		SaberPose rest = this.restPose(time, firstPerson);
		if (this.action == null) {
			return rest;
		}

		SaberPose from = firstPerson ? this.actionFromFp : this.actionFromTp;
		float e = Math.max(0f, time - this.actionStart);

		return switch (this.action) {
			case SWING -> this.move != null ? SaberGeometry.movePose(this.move, e, from, rest) : rest;
			case RECOIL, CLASH -> this.bounce(from, rest, e, 1.0f, 2.5f);
			case BLOCK_HIT -> this.bounce(from, rest, e, 0.45f, 1.5f);
			case STAGGER, GUARD_BREAK -> this.stagger(from, rest, e);
			case PARRY -> this.parry(rest, e);
			case THROW -> this.throwPose(from, rest, e);
			case CATCH -> SaberGeometry.swing(SwingDirection.STAB, 1f).blend(rest, SaberGeometry.easeOutCubic(e / this.actionDuration));
			case CANCEL -> rest;
		};
	}

	private SaberPose restPose(float time, boolean firstPerson) {
		float b = SaberGeometry.easeInOutCubic(Mth.lerp(time - (int) time, this.prevBlock, this.block));
		SaberPose idle = firstPerson ? FP_IDLE : TP_IDLE;
		SaberPose guard = firstPerson ? FP_BLOCK : TP_BLOCK;
		SaberPose rest = idle.blend(guard, b);

		// Leichtes "Atmen" in der Grundhaltung.
		float sway = Mth.sin(time * 0.07f) * 0.012f * (1f - b);
		return rest.translate(0, sway, 0).rotateBlade(0, 0, 1, Mth.sin(time * 0.05f) * 0.03f * (1f - b));
	}

	private SaberPose bounce(SaberPose from, SaberPose rest, float e, float strength, float outTicks) {
		SaberPose bounced = SaberGeometry.bounce(from, this.direction, strength);
		if (e < outTicks) {
			return from.blend(bounced, SaberGeometry.easeOutCubic(e / outTicks));
		}
		return bounced.blend(rest, SaberGeometry.easeInOutCubic((e - outTicks) / Math.max(1f, this.actionDuration - outTicks)));
	}

	private SaberPose stagger(SaberPose from, SaberPose rest, float e) {
		SaberPose thrown = SaberGeometry.STAGGERED;
		float out = 3f;
		if (e < out) {
			return from.blend(thrown, SaberGeometry.easeOutCubic(e / out));
		}
		float hold = this.actionDuration * 0.45f;
		if (e < hold) {
			float wobble = Mth.sin(e * 1.3f) * 0.06f * (1f - e / hold);
			return thrown.rotateBlade(0, 0, 1, wobble);
		}
		return thrown.blend(rest, SaberGeometry.easeInOutCubic((e - hold) / Math.max(1f, this.actionDuration - hold)));
	}

	private SaberPose parry(SaberPose rest, float e) {
		// Kurzer, kräftiger Ruck der Klinge nach vorne-oben.
		float t = e / this.actionDuration;
		float bump = Mth.sin(Mth.clamp(t, 0, 1) * Mth.PI);
		return rest.translate(0, 0.04f * bump, 0.08f * bump).rotateBlade(1, 0, 0, 0.35f * bump);
	}

	private SaberPose throwPose(SaberPose from, SaberPose rest, float e) {
		SaberPose release = SaberPose.of(0.25f, -0.15f, 0.70f, 0.6f, 0.2f, 0.8f);
		float t = e / this.actionDuration;
		return t < 0.5f ? from.blend(release, SaberGeometry.easeOutCubic(t * 2)) : release.blend(rest, SaberGeometry.easeInOutCubic((t - 0.5f) * 2));
	}

	// ------------------------------------------------------------------------------------------
	// Zustandsabfragen für Rendering & Sound
	// ------------------------------------------------------------------------------------------

	/** Klingenlänge 0..1 inkl. Teil-Tick, mit weicher Kurve. */
	public float extension(float partialTick) {
		float raw = Mth.lerp(partialTick, this.prevExtension, this.extension);
		return SaberGeometry.easeOutCubic(raw);
	}

	public boolean isThrown() {
		return this.thrown;
	}

	public boolean isVisible() {
		return !this.thrown;
	}

	public @Nullable SaberAnimation action() {
		return this.action;
	}

	public boolean isSwinging(float time) {
		return this.action == SaberAnimation.SWING && this.move != null;
	}

	/** Winkelgeschwindigkeit der Klinge (Grad pro Tick), für Brumm-Tonhöhe und Schweif. */
	public float bladeSpeed(float time) {
		SaberPose now = this.pose(time, false);
		SaberPose before = this.pose(time - 1f, false);
		return now.angleTo(before);
	}
}
