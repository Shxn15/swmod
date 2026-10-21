package com.shxn15.swmod.lightsaber;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.shxn15.swmod.ModItems;
import com.shxn15.swmod.registry.ModDataComponents;

/** Kleine Helfer rund um Lichtschwert-Items. */
public final class Lightsabers {
	private Lightsabers() {
	}

	public static boolean isLightsaber(ItemStack stack) {
		return stack.is(ModItems.LIGHTSABER);
	}

	public static LightsaberData data(ItemStack stack) {
		return stack.getOrDefault(ModDataComponents.LIGHTSABER, LightsaberData.DEFAULT);
	}

	public static boolean isIgnited(ItemStack stack) {
		return isLightsaber(stack) && data(stack).ignited();
	}

	public static boolean isThrown(ItemStack stack, Level level) {
		return isLightsaber(stack) && data(stack).isThrown(level.getGameTime());
	}

	/** Klinge an und das Schwert ist in der Hand (nicht gerade geworfen): bereit zum Kämpfen. */
	public static boolean isReady(ItemStack stack, Level level) {
		return isIgnited(stack) && !data(stack).isThrown(level.getGameTime());
	}

	/** Hält das Wesen ein kampfbereites Lichtschwert in der Haupthand? */
	public static boolean isReadyInMainHand(LivingEntity entity) {
		return isReady(entity.getMainHandItem(), entity.level());
	}

	public static int color(ItemStack stack) {
		return data(stack).color();
	}

	public static ItemStack create(BladeColor color) {
		ItemStack stack = new ItemStack(ModItems.LIGHTSABER);
		stack.set(ModDataComponents.LIGHTSABER, LightsaberData.ofColor(color.rgb));
		return stack;
	}
}
