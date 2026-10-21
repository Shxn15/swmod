package com.shxn15.swmod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.client.render.FirstPersonSaberRenderer;
import com.shxn15.swmod.lightsaber.Lightsabers;

/** Ersetzt die Ego-Darstellung des Lichtschwerts durch unsere animierte Version. */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
	private void swmod$renderLightsaber(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
										float attackProgress, ItemStack stack, float equipProgress, PoseStack poseStack,
										SubmitNodeCollector collector, int light, CallbackInfo ci) {
		if (hand == InteractionHand.MAIN_HAND && Lightsabers.isLightsaber(stack)) {
			FirstPersonSaberRenderer.render(player, stack, partialTick, equipProgress, poseStack, collector, light);
			ci.cancel();
		}
	}
}
