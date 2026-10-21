package com.shxn15.swmod.force;

import java.util.Collection;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /force} für Admins und zum Testen:
 * <ul>
 *     <li>{@code /force learn <Spieler> <Fähigkeit> [Stufe]}</li>
 *     <li>{@code /force learnall <Spieler>}: alles auf Höchststufe, Rad belegt</li>
 *     <li>{@code /force reset <Spieler>}: alles vergessen</li>
 *     <li>{@code /force refill <Spieler>}: Macht auffüllen, Abklingzeiten zurücksetzen</li>
 * </ul>
 */
final class ForceCommand {
	private static final DynamicCommandExceptionType UNKNOWN_ABILITY = new DynamicCommandExceptionType(
			id -> Component.translatable("commands.swmod.force.unknown", String.valueOf(id)));

	private ForceCommand() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("force")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("learn")
						.then(Commands.argument("targets", EntityArgument.players())
								.then(Commands.argument("ability", IdentifierArgument.id())
										.suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ForceAbilities.REGISTRY.keySet(), builder))
										.executes(context -> learn(context.getSource(), EntityArgument.getPlayers(context, "targets"),
												IdentifierArgument.getId(context, "ability"), -1))
										.then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
												.executes(context -> learn(context.getSource(), EntityArgument.getPlayers(context, "targets"),
														IdentifierArgument.getId(context, "ability"), IntegerArgumentType.getInteger(context, "level")))))))
				.then(Commands.literal("learnall")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> learnAll(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
				.then(Commands.literal("reset")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> reset(context.getSource(), EntityArgument.getPlayers(context, "targets")))))
				.then(Commands.literal("refill")
						.then(Commands.argument("targets", EntityArgument.players())
								.executes(context -> refill(context.getSource(), EntityArgument.getPlayers(context, "targets"))))));
	}

	private static int learn(CommandSourceStack source, Collection<ServerPlayer> players, Identifier id, int level) throws CommandSyntaxException {
		ForceAbility ability = ForceAbilities.REGISTRY.getValue(id);
		if (ability == null) {
			throw UNKNOWN_ABILITY.create(id);
		}
		int target = level < 0 ? ability.maxLevel() : Math.min(level, ability.maxLevel());
		for (ServerPlayer player : players) {
			ForceData data = ForceSystem.data(player).withLevel(id, target);
			if (target > 0 && data.slotOf(id) < 0 && data.firstFreeSlot() >= 0) {
				data = data.withSlot(data.firstFreeSlot(), id);
			}
			ForceSystem.setData(player, data);
		}
		source.sendSuccess(() -> Component.translatable("commands.swmod.force.learn", ability.name(), target, players.size()), true);
		return players.size();
	}

	private static int learnAll(CommandSourceStack source, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			ForceData data = ForceSystem.data(player);
			int slot = 0;
			for (ForceAbility ability : ForceAbilities.REGISTRY) {
				data = data.withLevel(ability.id(), ability.maxLevel());
				if (slot < ForceData.WHEEL_SLOTS) {
					data = data.withSlot(slot++, ability.id());
				}
			}
			ForceSystem.setData(player, data);
			ForceSystem.refill(player);
		}
		source.sendSuccess(() -> Component.translatable("commands.swmod.force.learnall", players.size()), true);
		return players.size();
	}

	private static int reset(CommandSourceStack source, Collection<ServerPlayer> players) {
		for (ServerPlayer player : players) {
			ForceSystem.reset(player);
			ForceSystem.setData(player, ForceData.EMPTY);
		}
		source.sendSuccess(() -> Component.translatable("commands.swmod.force.reset", players.size()), true);
		return players.size();
	}

	private static int refill(CommandSourceStack source, Collection<ServerPlayer> players) {
		players.forEach(ForceSystem::refill);
		source.sendSuccess(() -> Component.translatable("commands.swmod.force.refill", players.size()), true);
		return players.size();
	}
}
