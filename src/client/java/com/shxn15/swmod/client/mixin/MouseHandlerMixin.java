package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MouseHandler;

import com.shxn15.swmod.client.force.ForceInput;
import com.shxn15.swmod.client.input.SaberInput;

/**
 * Liest die Mausbewegung für die Schlaggesten mit und dämpft die Kameradrehung,
 * solange man mit gehaltener linker Maustaste einen Schlag "zeichnet".
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"))
	private void swmod$saberGesture(double movementTime, CallbackInfo ci) {
		if (this.accumulatedDX == 0 && this.accumulatedDY == 0) {
			return;
		}

		// Offenes Fähigkeitenrad: die Maus bewegt den Wahlzeiger, nicht die Kamera.
		if (ForceInput.onMouseMove(this.accumulatedDX, this.accumulatedDY)) {
			this.accumulatedDX = 0;
			this.accumulatedDY = 0;
			return;
		}

		double scale = SaberInput.onMouseMove(this.accumulatedDX, this.accumulatedDY);
		this.accumulatedDX *= scale;
		this.accumulatedDY *= scale;
	}
}
