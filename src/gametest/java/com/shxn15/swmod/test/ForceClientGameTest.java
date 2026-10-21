package com.shxn15.swmod.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.CameraType;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.shxn15.swmod.ModItems;
import com.shxn15.swmod.SwMod;
import com.shxn15.swmod.client.force.ForceClientState;
import com.shxn15.swmod.client.force.ForceInput;
import com.shxn15.swmod.client.force.HolocronScreen;
import com.shxn15.swmod.force.ForceAbilities;
import com.shxn15.swmod.force.ForceAbility;
import com.shxn15.swmod.force.ForceData;
import com.shxn15.swmod.force.ForceSystem;
import com.shxn15.swmod.lightsaber.BladeColor;
import com.shxn15.swmod.lightsaber.Lightsabers;
import com.shxn15.swmod.registry.ModDataComponents;

/**
 * Spielt das Fähigkeitensystem in einem echten Minecraft durch: Holocron (Lernen, Rad belegen),
 * Fähigkeitenrad mit C, jede Fähigkeit einmal an Zombies, HUD. Screenshots landen in
 * build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class ForceClientGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			TestServerContext server = world.getServer();
			TestInput input = context.getInput();

			server.runCommand("time set midnight");
			server.runCommand("gamerule advance_time false");
			server.runCommand("weather clear");
			server.runCommand("gamemode survival @a");
			server.runCommand("effect give @a minecraft:resistance infinite 4 true");
			server.runCommand("effect give @a minecraft:saturation infinite 0 true");
			input.moveCursor(1, 0);
			context.waitTicks(2);
			input.lookAt(0f, 0f);
			context.waitTicks(5);

			this.testHolocron(context, server);
			this.testWheel(context, server, input);
			this.testAbilities(context, server, input);
		}

		if (!this.failures.isEmpty()) {
			throw new AssertionError("Machttest fehlgeschlagen:\n - " + String.join("\n - ", this.failures));
		}
	}

	// ------------------------------------------------------------------------------------------
	// Holocron
	// ------------------------------------------------------------------------------------------

	private void testHolocron(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HOLOCRON));
			player.giveExperienceLevels(60);
		});
		context.waitTicks(12); // Ausrüst-Animation abwarten
		context.takeScreenshot("30_holocron_in_hand");

		// Rechtsklick mit dem Holocron öffnet seinen Bildschirm.
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(3);
		this.check(context.computeOnClient(mc -> mc.gui.screen() instanceof HolocronScreen), "Rechtsklick mit Holocron sollte den Holocron-Bildschirm öffnen");
		if (!context.computeOnClient(mc -> mc.gui.screen() instanceof HolocronScreen)) {
			context.setScreen(HolocronScreen::new);
		}
		context.waitTicks(25);
		context.takeScreenshot("31_holocron_screen");

		// "Lernen" anklicken (Machtstoß ist am Anfang ausgewählt).
		this.clickLearnButton(context);
		context.waitTicks(4);
		context.takeScreenshot("32_holocron_learning");
		context.waitTicks(10);
		int pushLevel = server.computeOnServer(s -> ForceSystem.data(player(s)).level(ForceAbilities.PUSH));
		int xp = server.computeOnServer(s -> player(s).experienceLevel);
		this.check(pushLevel == 1, "Machtstoß sollte nach Klick auf Lernen Stufe 1 haben, hat " + pushLevel);
		this.check(xp == 60 - ForceAbilities.PUSH.learnCost(1), "Lernen sollte " + ForceAbilities.PUSH.learnCost(1) + " Level kosten, übrig: " + xp);
		this.check(server.computeOnServer(s -> ForceSystem.data(player(s)).slotOf(ForceAbilities.PUSH.id()) >= 0),
				"Neu gelernte Fähigkeit sollte automatisch im Rad liegen");

		// Alles lernen und noch einmal ansehen.
		server.runCommand("force learnall @a");
		context.waitTicks(10);
		context.takeScreenshot("33_holocron_all_learned");
		context.setScreen(() -> null);
		context.waitTicks(2);
	}

	private void clickLearnButton(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (mc.gui.screen() instanceof HolocronScreen screen) {
				// Der Knopf sitzt unten in der rechten Tafel.
				int panelW = Math.clamp((int) (screen.width * 0.34f), 140, 190);
				double x = screen.width - 8 - panelW / 2.0;
				double y = screen.height - 10 - 9 - 8;
				MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(1, 0));
				screen.mouseClicked(event, false);
			}
		});
	}

	// ------------------------------------------------------------------------------------------
	// Fähigkeitenrad
	// ------------------------------------------------------------------------------------------

	private void testWheel(ClientGameTestContext context, TestServerContext server, TestInput input) {
		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
		// Nach dem Schließen eines Bildschirms verwirft Minecraft die erste Mausbewegung.
		input.moveCursor(1, 0);
		context.waitTicks(3);

		input.holdKey(ForceInput.WHEEL);
		context.waitTicks(6);
		this.check(context.computeOnClient(mc -> ForceInput.isWheelOpen()), "C sollte das Fähigkeitenrad öffnen");
		input.moveCursor(220, 0); // nach rechts: Platz 3 (Osten)
		context.waitTicks(4);
		int hovered = context.computeOnClient(mc -> ForceInput.hovered());
		this.check(hovered == 2, "Maus nach rechts sollte Radplatz 3 anvisieren, ist " + hovered);
		context.takeScreenshot("34_wheel");
		input.releaseKey(ForceInput.WHEEL);
		context.waitTicks(6);
		this.check(!context.computeOnClient(mc -> ForceInput.isWheelOpen()), "Loslassen von C sollte das Rad schließen");
		int selected = server.computeOnServer(s -> ForceSystem.data(player(s)).selected());
		this.check(selected == 2, "Auswahl sollte beim Server angekommen sein (Platz 3), ist " + selected);
		context.waitTicks(6);
		context.takeScreenshot("35_hud_selected");
	}

	// ------------------------------------------------------------------------------------------
	// Fähigkeiten
	// ------------------------------------------------------------------------------------------

	private void testAbilities(ClientGameTestContext context, TestServerContext server, TestInput input) {
		// Machtstoß: Zombie fliegt weg.
		this.select(context, server, ForceAbilities.PUSH);
		this.summonZombie(server, 3.0);
		Vec3 before = zombiePos(server);
		input.pressKey(options -> options.keyUse);
		context.waitTicks(3);
		context.takeScreenshot("40_push");
		context.waitTicks(15);
		Vec3 after = zombiePos(server);
		this.check(after != null && before != null && after.distanceTo(before) > 2.0, "Machtstoß sollte den Zombie wegschleudern (" + before + " -> " + after + ")");
		this.refill(server);

		// Machtzug: Zombie kommt näher.
		this.clearZombies(server);
		this.select(context, server, ForceAbilities.PULL);
		this.summonZombie(server, 9.0);
		context.waitTicks(5);
		double distanceBefore = zombieDistance(server);
		input.pressKey(options -> options.keyUse);
		context.waitTicks(3);
		context.takeScreenshot("41_pull");
		context.waitTicks(20);
		double distanceAfter = zombieDistance(server);
		this.check(distanceAfter < distanceBefore - 2.0, "Machtzug sollte den Zombie heranziehen (" + distanceBefore + " -> " + distanceAfter + ")");
		this.refill(server);

		// Machtgriff: Zombie schwebt mit Leuchten vor einem.
		this.clearZombies(server);
		this.select(context, server, ForceAbilities.GRAB);
		this.summonZombie(server, 4.0);
		context.waitTicks(5);
		double yBefore = zombiePos(server).y;
		input.lookAt(0f, -12f);
		input.holdKey(options -> options.keyUse);
		context.waitTicks(20);
		double yHeld = zombiePos(server).y;
		this.check(yHeld > yBefore + 0.5, "Machtgriff sollte den Zombie hochheben (y " + yBefore + " -> " + yHeld + ")");
		context.takeScreenshot("42_grab");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(4);
		context.takeScreenshot("43_grab_third_person");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		input.releaseKey(options -> options.keyUse);
		input.lookAt(0f, 0f);
		context.waitTicks(30);
		this.refill(server);

		// Machtblitze: mehrere Zombies werden getroffen.
		this.clearZombies(server);
		this.select(context, server, ForceAbilities.LIGHTNING);
		this.summonZombie(server, 4.0);
		this.summonZombie(server, "^1.5 ^ ^5");
		this.summonZombie(server, "^-1.5 ^ ^5");
		context.waitTicks(5);
		input.holdKey(options -> options.keyUse);
		context.waitTicks(12);
		context.takeScreenshot("44_lightning");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(3);
		context.takeScreenshot("45_lightning_third_person");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);
		input.releaseKey(options -> options.keyUse);
		float lightningHealth = server.computeOnServer(s -> lowestZombieHealth(s));
		// Stufe 3: alle 4 Ticks 2 Schaden, nach gut einer Sekunde sollte also deutlich etwas fehlen.
		this.check(lightningHealth > 0f && lightningHealth < 14f, "Machtblitze sollten Zombies spürbar verletzen (Leben " + lightningHealth + ")");
		context.waitTicks(10);
		this.refill(server);

		// Machtwürgen: Zombie wird hochgehoben und verliert Leben.
		this.clearZombies(server);
		this.select(context, server, ForceAbilities.GRIP);
		this.summonZombie(server, 4.0);
		context.waitTicks(5);
		double gripY = zombiePos(server).y;
		input.holdKey(options -> options.keyUse);
		context.waitTicks(25);
		context.takeScreenshot("46_grip");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(3);
		context.takeScreenshot("47_grip_front");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		double gripHeldY = zombiePos(server).y;
		float gripHealth = server.computeOnServer(s -> lowestZombieHealth(s));
		input.releaseKey(options -> options.keyUse);
		this.check(gripHeldY > gripY + 0.5, "Machtwürgen sollte den Zombie anheben (y " + gripY + " -> " + gripHeldY + ")");
		this.check(gripHealth < 19f, "Machtwürgen sollte Leben kosten (Leben " + gripHealth + ")");
		context.waitTicks(20);
		this.refill(server);

		// Machtheilung
		this.clearZombies(server);
		this.select(context, server, ForceAbilities.HEAL);
		server.runOnServer(s -> player(s).setHealth(6f));
		context.waitTicks(3);
		input.pressKey(options -> options.keyUse);
		context.waitTicks(6);
		context.takeScreenshot("48_heal");
		float health = server.computeOnServer(s -> player(s).getHealth());
		this.check(health > 10f, "Machtheilung sollte heilen (Leben " + health + ")");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		input.pressKey(options -> options.keyUse); // Abklingzeit: darf nichts tun
		context.waitTicks(4);
		context.takeScreenshot("49_heal_third_person");
		context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(30);
		this.refill(server);

		// Machtgeschwindigkeit
		this.select(context, server, ForceAbilities.SPEED);
		input.pressKey(options -> options.keyUse);
		context.waitTicks(4);
		boolean fast = server.computeOnServer(s -> player(s).getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(SwMod.id("force_speed")));
		this.check(fast, "Machtgeschwindigkeit sollte die Laufgeschwindigkeit erhöhen");
		input.holdKey(options -> options.keyUp);
		context.waitTicks(12);
		context.takeScreenshot("50_speed");
		input.releaseKey(options -> options.keyUp);
		context.waitTicks(5);

		// HUD: Machtleiste unter der XP-Leiste nach etwas Verbrauch.
		this.select(context, server, ForceAbilities.PUSH);
		server.runOnServer(s -> player(s).setItemInHand(InteractionHand.MAIN_HAND, this.saber()));
		context.waitTicks(3);
		input.pressKey(options -> options.keyUse);
		context.waitTicks(6);
		context.takeScreenshot("51_hud_force_bar");
		float force = context.computeOnClient(mc -> ForceClientState.force());
		float max = context.computeOnClient(mc -> ForceClientState.maxForce());
		this.check(force < max, "Der Machtvorrat sollte nach dem Wirken sinken (" + force + "/" + max + ")");
		SwMod.LOGGER.info("[Test] Machtvorrat nach Stoß: {}/{}", force, max);
	}

	private ItemStack saber() {
		ItemStack saber = Lightsabers.create(BladeColor.GREEN);
		saber.set(ModDataComponents.LIGHTSABER, Lightsabers.data(saber).withIgnited(true));
		return saber;
	}

	/** Wählt eine Fähigkeit wie über das Rad (Platz suchen, Auswahl an den Server). */
	private void select(ClientGameTestContext context, TestServerContext server, ForceAbility ability) {
		server.runOnServer(s -> {
			ServerPlayer player = player(s);
			ForceData data = ForceSystem.data(player);
			int slot = data.slotOf(ability.id());
			ForceSystem.setData(player, data.withSelected(slot));
		});
		context.waitTicks(3);
		ForceAbility selected = context.computeOnClient(mc -> ForceClientState.selected());
		this.check(selected == ability, "Auswahl von " + ability.id() + " sollte beim Client ankommen, ist " + (selected != null ? selected.id() : "keine"));
	}

	private void refill(TestServerContext server) {
		server.runCommand("force refill @a");
	}

	/** Zombie mit KI (sonst bewegt ihn nichts), aber so langsam, dass er stehen bleibt. */
	private void summonZombie(TestServerContext server, double distance) {
		this.summonZombie(server, "^ ^ ^" + distance);
	}

	private void summonZombie(TestServerContext server, String position) {
		server.runCommand("execute as @p at @p run summon minecraft:zombie " + position + " {PersistenceRequired:1b,Silent:1b,IsBaby:0b,CanPickUpLoot:0b}");
		server.runCommand("effect give @e[type=minecraft:zombie] minecraft:slowness infinite 10 true");
	}

	private void clearZombies(TestServerContext server) {
		server.runCommand("kill @e[type=minecraft:zombie]");
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static Vec3 zombiePos(TestServerContext server) {
		return server.computeOnServer(s -> {
			for (Entity entity : s.overworld().getAllEntities()) {
				if (entity instanceof Zombie zombie && zombie.isAlive()) {
					return zombie.position();
				}
			}
			return null;
		});
	}

	private static double zombieDistance(TestServerContext server) {
		return server.computeOnServer(s -> {
			ServerPlayer player = player(s);
			for (Entity entity : s.overworld().getAllEntities()) {
				if (entity instanceof Zombie zombie && zombie.isAlive()) {
					return (double) zombie.distanceTo(player);
				}
			}
			return -1.0;
		});
	}

	private static float lowestZombieHealth(MinecraftServer server) {
		float lowest = 1000f;
		boolean any = false;
		for (Entity entity : server.overworld().getAllEntities()) {
			if (entity instanceof Zombie zombie && zombie.isAlive()) {
				lowest = Math.min(lowest, zombie.getHealth());
				any = true;
			}
		}
		return any ? lowest : -1f;
	}

	private void check(boolean condition, String message) {
		if (!condition) {
			this.failures.add(message);
			SwMod.LOGGER.error("[Test] FEHLER: {}", message);
		} else {
			SwMod.LOGGER.info("[Test] ok: {}", message);
		}
	}
}
