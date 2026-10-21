package com.shxn15.swmod.client.item;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import com.shxn15.swmod.lightsaber.Lightsabers;

/** Item-Modell-Bedingung: ist die Klinge gezündet? ({@code "property": "swmod:lightsaber_ignited"}) */
public record LightsaberIgnitedProperty() implements ConditionalItemModelProperty {
	public static final MapCodec<LightsaberIgnitedProperty> MAP_CODEC = MapCodec.unit(new LightsaberIgnitedProperty());

	@Override
	public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext displayContext) {
		return Lightsabers.isIgnited(stack);
	}

	@Override
	public MapCodec<LightsaberIgnitedProperty> type() {
		return MAP_CODEC;
	}
}
