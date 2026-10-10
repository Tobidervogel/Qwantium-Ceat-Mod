package dev.qwantium.pvpbot.gametest;

import dev.qwantium.pvpbot.client.BotPermission;
import dev.qwantium.pvpbot.client.ClientConfig;
import dev.qwantium.pvpbot.client.bot.BotController;
import dev.qwantium.pvpbot.client.bot.Difficulty;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * Starts the real game, builds a flat test world and lets the bot fight. Run with
 * {@code ./gradlew runClientGameTest}; screenshots end up in {@code build/run/clientGameTest/screenshots}.
 */
public class BotFightClientGameTest implements FabricClientGameTest {
	/** Surface of the gametest flat world. */
	private static final int GROUND_Y = -60;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(minecraft -> minecraft.options.pauseOnLostFocus = false);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getClientWorld().waitForChunksRender();
			TestServerContext server = singleplayer.getServer();
			server.runCommand("difficulty normal");
			server.runCommand("gamerule spawn_mobs false");

			context.runOnClient(minecraft -> {
				ClientConfig config = BotController.get().config();
				config.targetMode = TargetMode.NEAREST_ANY;
				config.difficulty = Difficulty.HARD;
				config.autoRespawn = true;
			});

			killsStandingTarget(context, server);
			winsAgainstFightingHusk(context, server);
			eatsGoldenAppleWhenLow(context, server);
			doesNotWalkIntoLava(context, server);

			HeroBotDuel duel = new HeroBotDuel(context, server);
			for (Difficulty difficulty : new Difficulty[] {Difficulty.NORMAL, Difficulty.HARD, Difficulty.QUANTUM}) {
				HeroBotDuel.Result result = duel.play(difficulty, 3);
				if (result.wins() + result.losses() == 0) {
					throw new AssertionError("No one died in any " + difficulty.id() + " duel round; the bot is not fighting players");
				}
			}
			duel.remove();
		}

		allowedOnServerWithMod(context);
	}

	/** The bot walks up to a target that does not move, aims and kills it. */
	private static void killsStandingTarget(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 8 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(5);

		setBot(context, true);
		context.waitTicks(15);
		context.takeScreenshot("qwantiumbot_standing_target");
		int ticks = context.waitFor(minecraft -> huskCount(minecraft) == 0, 400);
		assertAlive(context, "standing target");
		setBot(context, false);
		System.out.println("[qwantiumbot-gametest] standing target killed after " + ticks + " ticks");
	}

	/** A husk that fights back: the bot has to win with sword and shield. */
	private static void winsAgainstFightingHusk(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
		server.runCommand("item replace entity @p armor.chest with minecraft:iron_chestplate");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 6 {PersistenceRequired:1b}");
		context.waitTicks(5);

		setBot(context, true);
		context.waitTicks(25);
		context.takeScreenshot("qwantiumbot_fighting_husk");
		int ticks = context.waitFor(minecraft -> huskCount(minecraft) == 0, 600);
		float health = context.computeOnClient(minecraft -> minecraft.player.getHealth());
		assertAlive(context, "fighting husk");
		setBot(context, false);
		System.out.println("[qwantiumbot-gametest] fighting husk killed after " + ticks + " ticks, health left " + health);
	}

	/** At low health the bot backs off and eats a golden apple from the hotbar. */
	private static void eatsGoldenAppleWhenLow(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.1 with minecraft:golden_apple 2");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 10 {NoAI:1b,PersistenceRequired:1b}");
		server.runCommand("damage @p 12 minecraft:generic");
		context.waitTicks(5);

		setBot(context, true);
		context.waitFor(minecraft -> minecraft.player.getInventory().countItem(Items.GOLDEN_APPLE) == 1, 200);
		boolean absorption = context.computeOnClient(minecraft -> minecraft.player.hasEffect(MobEffects.ABSORPTION));
		if (!absorption) {
			throw new AssertionError("Bot ate, but has no absorption effect");
		}
		context.waitTicks(3);
		int weaponSlot = context.computeOnClient(minecraft -> minecraft.player.getInventory().getSelectedSlot());
		if (weaponSlot != 0) {
			throw new AssertionError("Bot did not switch back to the sword after eating (slot " + weaponSlot + ")");
		}
		setBot(context, false);
		System.out.println("[qwantiumbot-gametest] ate a golden apple at low health");
	}

	/** A lava trench lies between the bot and its target; the bot must not walk into it. */
	private static void doesNotWalkIntoLava(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("fill -12 " + (GROUND_Y - 1) + " 4 12 " + (GROUND_Y - 1) + " 5 minecraft:lava");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 9 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(5);

		setBot(context, true);
		for (int tick = 0; tick < 160; tick++) {
			context.waitTick();
			boolean burning = context.computeOnClient(minecraft -> minecraft.player.isInLava() || minecraft.player.isOnFire());
			if (burning) {
				context.takeScreenshot("qwantiumbot_lava_fail");
				throw new AssertionError("Bot walked into lava after " + tick + " ticks");
			}
		}
		boolean engaged = context.computeOnClient(minecraft -> BotController.get().isEngaged());
		if (!engaged) {
			throw new AssertionError("Bot should still be engaged with the husk across the lava");
		}
		context.takeScreenshot("qwantiumbot_lava_edge");
		setBot(context, false);
		server.runCommand("fill -12 " + (GROUND_Y - 1) + " 4 12 " + (GROUND_Y - 1) + " 5 minecraft:grass_block");
		System.out.println("[qwantiumbot-gametest] stayed out of the lava");
	}

	/** On a dedicated server that runs the mod, the server allows the bot. */
	private static void allowedOnServerWithMod(ClientGameTestContext context) {
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
			TestServerConnection connection = server.connect()) {
			connection.getClientWorld().waitForChunksDownload();
			context.waitTicks(5);
			boolean allowed = context.computeOnClient(minecraft -> !minecraft.hasSingleplayerServer() && BotPermission.isAllowed(minecraft));
			if (!allowed) {
				throw new AssertionError("Server with the mod should allow the bot");
			}
			context.runOnClient(minecraft -> BotController.get().setActive(minecraft, true));
			context.waitTicks(2);
			boolean active = context.computeOnClient(minecraft -> BotController.get().isActive());
			if (!active) {
				throw new AssertionError("Bot should turn on on a server that allows it");
			}
			System.out.println("[qwantiumbot-gametest] dedicated server with the mod allows the bot");
		}

		boolean offAfterLeaving = context.computeOnClient(minecraft -> !BotController.get().isActive());
		if (!offAfterLeaving) {
			throw new AssertionError("Bot should switch off when leaving the server");
		}
	}

	private static void resetArena(ClientGameTestContext context, TestServerContext server) {
		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("gamemode survival @p");
		server.runCommand("clear @p");
		server.runCommand("effect clear @p");
		server.runCommand("effect give @p minecraft:instant_health 1 10 true");
		server.runCommand("effect give @p minecraft:saturation 1 10 true");
		server.runCommand("tp @p 0 " + GROUND_Y + " 0 0 0");
		context.waitTicks(10);
	}

	private static void setBot(ClientGameTestContext context, boolean active) {
		context.runOnClient(minecraft -> BotController.get().setActive(minecraft, active));
	}

	private static void assertAlive(ClientGameTestContext context, String scenario) {
		boolean alive = context.computeOnClient(minecraft -> minecraft.player.isAlive());
		if (!alive) {
			throw new AssertionError("Bot died in scenario: " + scenario);
		}
	}

	private static int huskCount(Minecraft minecraft) {
		return minecraft.level.getEntitiesOfClass(Husk.class, new AABB(-40, -70, -40, 40, -40, 40), Husk::isAlive).size();
	}
}
