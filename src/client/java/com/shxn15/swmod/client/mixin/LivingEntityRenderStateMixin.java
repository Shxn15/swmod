package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.shxn15.swmod.client.render.SaberRenderData;

/** Hängt unsere Lichtschwert-Daten an jeden Render-State eines Lebewesens. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements SaberRenderData.Access {
	@Unique
	private final SaberRenderData swmod$saberData = new SaberRenderData();

	@Override
	public SaberRenderData swmod$saberData() {
		return this.swmod$saberData;
	}
}
