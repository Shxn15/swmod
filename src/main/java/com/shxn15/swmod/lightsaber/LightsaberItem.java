package com.shxn15.swmod.lightsaber;

import java.util.function.Predicate;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

import com.shxn15.swmod.combat.SaberCombatSystem;

/**
 * Das Lichtschwert. Angriffe laufen über das eigene Kampfsystem (Linksklick + Mausrichtung),
 * Rechtsklick hält die Deckung über die normale Item-Benutzung, damit der Server genau weiß,
 * seit wann geblockt wird (wichtig für das Parier-Zeitfenster).
 */
public class LightsaberItem extends Item {
	/** Wird vom Client gesetzt, damit er Blocken während eines Taumelns gar nicht erst beginnt. */
	public static Predicate<Player> clientCanBlock = player -> true;

	public LightsaberItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (hand != InteractionHand.MAIN_HAND || !Lightsabers.isReady(stack, level)) {
			return InteractionResult.PASS;
		}

		boolean allowed = level.isClientSide() ? clientCanBlock.test(player) : SaberCombatSystem.canStartBlocking(player);
		if (!allowed) {
			return InteractionResult.FAIL;
		}

		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.BLOCK;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity entity) {
		return 72000;
	}
}
