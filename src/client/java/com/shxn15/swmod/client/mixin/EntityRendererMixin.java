package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

import com.shxn15.swmod.client.force.fx.ForceRenderer;

/** Farbiger Umriss um alles, was gerade von der Macht gehalten wird. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
			at = @At("TAIL"))
	private void swmod$forceOutline(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
		ForceRenderer.extractOutline(entity, state, partialTicks);
	}
}
