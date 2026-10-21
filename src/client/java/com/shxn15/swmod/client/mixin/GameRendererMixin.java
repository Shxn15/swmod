package com.shxn15.swmod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

import com.shxn15.swmod.client.force.fx.ForceScreenEffects;

/** Kamerawackeln bei Machtstößen, Aufprallen und Blitztreffern. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void swmod$forceShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
		ForceScreenEffects.applyShake(poseStack);
	}
}
