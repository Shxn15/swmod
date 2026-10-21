package com.shxn15.swmod.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Löst das Griff-Modell eines Lichtschwerts auf. Wir nutzen den Anzeige-Kontext NONE,
 * weil wir Position und Ausrichtung komplett selbst bestimmen.
 */
public final class HiltModels {
	private HiltModels() {
	}

	public static void update(ItemStackRenderState state, ItemStack stack, @Nullable LivingEntity owner) {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.NONE, minecraft.level, owner, 0);
	}
}
