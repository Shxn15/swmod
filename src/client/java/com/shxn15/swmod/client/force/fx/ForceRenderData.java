package com.shxn15.swmod.client.force.fx;

import org.jspecify.annotations.Nullable;

/**
 * Machteffekte eines Lebewesens für einen Frame, angehängt an seinen Render-State
 * (siehe {@code LivingEntityRenderStateMixin}).
 */
public final class ForceRenderData {
	/** Stärke des Leuchtens (0 = keins). */
	public float aura;
	public int auraColor;
	public float time;
	/** Laufende Wirk-Geste und wie lange sie schon läuft (Ticks inkl. Teil-Tick). */
	public ForceFx.@Nullable Gesture gesture;
	public float gestureTime;

	/** Implementiert von LivingEntityRenderState per Mixin. */
	public interface Access {
		ForceRenderData swmod$forceData();
	}
}
