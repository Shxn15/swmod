package com.shxn15.swmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/** Zugriff auf den GUI-Render-Zustand, um eigene Formen (Ringe, Bögen, Leuchten) einzureihen. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
	@Accessor("guiRenderState")
	GuiRenderState swmod$guiRenderState();
}
