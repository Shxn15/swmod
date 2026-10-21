package com.shxn15.swmod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.client.force.fx.ForceGestures;
import com.shxn15.swmod.client.render.FirstPersonSaberRenderer;
import com.shxn15.swmod.lightsaber.Lightsabers;

/** Ersetzt die Ego-Darstellung des Lichtschwerts durch unsere animierte Version. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsAndItemsRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void swmod$submitLightsaber(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
										float xRot, InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight,
										PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && Lightsabers.isLightsaber(stack)) {
			FirstPersonSaberRenderer.submit(inverseArmHeight, poseStack, collector, light);
			ci.cancel();
		} else if (hand == InteractionHand.OFF_HAND && stack.isEmpty() && playerState.avatarRenderState != null && ForceGestures.localGesture() != null) {
			// Machtgeste: die freie Hand streckt sich ins Bild.
			ForceGestures.renderFirstPersonArm(playerState, poseStack, collector, light, playerState.avatarRenderState.mainArm.getOpposite(), partialTicks);
			ci.cancel();
		}
	}
}
