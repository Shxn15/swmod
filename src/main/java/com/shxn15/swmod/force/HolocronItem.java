package com.shxn15.swmod.force;

import java.util.function.Consumer;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import com.shxn15.swmod.registry.ModSounds;

/**
 * Ein Jedi-Holocron. Rechtsklick öffnet sein Wissen: Fähigkeiten lernen, verbessern und auf das
 * Fähigkeitenrad legen. Das Holocron wird dabei nicht verbraucht.
 */
public class HolocronItem extends Item {
	/** Wird vom Client gesetzt und öffnet den Holocron-Bildschirm. */
	public static Consumer<Player> clientOpen = player -> {
	};

	public HolocronItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			clientOpen.accept(player);
		} else {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.HOLOCRON_OPEN, SoundSource.PLAYERS, 0.8f, 1.0f);
		}
		return InteractionResult.SUCCESS;
	}
}
