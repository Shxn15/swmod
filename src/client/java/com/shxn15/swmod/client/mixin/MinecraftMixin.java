package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;

import com.shxn15.swmod.client.force.ForceInput;

/** Mit gewählter Machtfähigkeit gehört der Rechtsklick der Macht: keine normale Benutzung. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void swmod$forceCapturesUse(CallbackInfo ci) {
		if (ForceInput.capturesUse()) {
			ci.cancel();
		}
	}
}
