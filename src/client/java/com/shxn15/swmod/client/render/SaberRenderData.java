package com.shxn15.swmod.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.item.ItemStackRenderState;

/**
 * Zusätzliche Render-Daten, die wir an den Render-State eines Wesens hängen
 * (siehe {@code LivingEntityRenderStateMixin}). Alle Vektoren im Modellraum des Wesens.
 */
public final class SaberRenderData {
	public boolean active;
	public final ItemStackRenderState hilt = new ItemStackRenderState();
	public SaberPoseSampler.@Nullable Sample sample;
	public int color;
	public float extension;
	public float time;

	/** Implementiert von LivingEntityRenderState per Mixin. */
	public interface Access {
		SaberRenderData swmod$saberData();
	}
}
