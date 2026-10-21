package com.shxn15.swmod.client.item;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.lightsaber.Lightsabers;

/** Färbt die Klinge im Inventar-Icon in der Farbe des Kristalls ({@code "type": "swmod:blade_color"}). */
public record BladeColorTintSource() implements ItemTintSource {
	public static final MapCodec<BladeColorTintSource> MAP_CODEC = MapCodec.unit(new BladeColorTintSource());

	@Override
	public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
		return ARGB.opaque(Lightsabers.color(stack));
	}

	@Override
	public MapCodec<? extends ItemTintSource> type() {
		return MAP_CODEC;
	}
}
