package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

import com.shxn15.swmod.client.force.fx.ForceRenderer;
import com.shxn15.swmod.client.render.ThirdPersonSaber;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void swmod$extractSaber(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
		ThirdPersonSaber.extract(entity, state, partialTick);
		ForceRenderer.extractLiving(entity, state, partialTick);
	}
}
