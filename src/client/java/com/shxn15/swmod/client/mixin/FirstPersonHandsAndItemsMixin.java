package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;

import com.shxn15.swmod.client.render.FirstPersonSaberRenderer;

/** Berechnet beim Extrahieren der Ego-Hände auch die Lichtschwertpose. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void swmod$extractLightsaber(LocalPlayer player, float partialTicks, FirstPersonHandsAndItemsRenderState state, CallbackInfo ci) {
		FirstPersonSaberRenderer.extract(player, state.mainHandItem, partialTicks);
	}
}
