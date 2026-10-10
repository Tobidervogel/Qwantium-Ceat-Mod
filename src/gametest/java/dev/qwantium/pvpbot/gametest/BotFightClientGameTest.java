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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
		if (Boolean.getBoolean("qwantiumbot.quantumBridgeOnly")) {
			QuantumMapDuel.runIfConfigured(context);
			return;
		}

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
			escapesCobwebWithWater(context, server);
			throwsCobwebAtKnockedBackOpponent(context, server);
			poursLavaUnderAirborneOpponent(context, server);

			HeroBotDuel duel = new HeroBotDuel(context, server);
			for (Difficulty difficulty : new Difficulty[] {Difficulty.NORMAL, Difficulty.HARD, Difficulty.CRAZY, Difficulty.QUANTUM}) {
				HeroBotDuel.Result result = duel.play(difficulty, Integer.getInteger("qwantiumbot.duelRounds", 3));
				if (result.wins() + result.losses() == 0) {
					throw new AssertionError("No one died in any " + difficulty.id() + " duel round; the bot is not fighting players");
				}
			}
			duel.remove();
		}

		allowedOnServerWithMod(context);
		QuantumMapDuel.runIfConfigured(context);
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

	/**
	 * A lava trench lies between the bot and its target; the bot must not walk or jump into it. The
	 * play style is random, so this runs a few times with the target at different spots.
	 */
	private static void doesNotWalkIntoLava(ClientGameTestContext context, TestServerContext server) {
		int[][] targets = {{0, 9}, {-4, 8}, {5, 10}};
		for (int attempt = 0; attempt < targets.length; attempt++) {
			resetArena(context, server);
			server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
			server.runCommand("fill -12 " + (GROUND_Y - 1) + " 4 12 " + (GROUND_Y - 1) + " 5 minecraft:lava");
			server.runCommand("summon minecraft:husk " + targets[attempt][0] + " " + GROUND_Y + " " + targets[attempt][1]
				+ " {NoAI:1b,PersistenceRequired:1b}");
			context.waitTicks(5);

			setBot(context, true);
			for (int tick = 0; tick < 160; tick++) {
				context.waitTick();
				boolean burning = context.computeOnClient(minecraft -> minecraft.player.isInLava() || minecraft.player.isOnFire());
				if (burning) {
					context.takeScreenshot("qwantiumbot_lava_fail");
					throw new AssertionError("Bot walked into lava after " + tick + " ticks (attempt " + (attempt + 1) + ")");
				}
			}
			boolean engaged = context.computeOnClient(minecraft -> BotController.get().isEngaged());
			if (!engaged) {
				throw new AssertionError("Bot should still be engaged with the husk across the lava");
			}
			if (attempt == 0) {
				context.takeScreenshot("qwantiumbot_lava_edge");
			}
			setBot(context, false);
			server.runCommand("fill -12 " + (GROUND_Y - 1) + " 4 12 " + (GROUND_Y - 1) + " 5 minecraft:grass_block");
		}
		System.out.println("[qwantiumbot-gametest] stayed out of the lava (" + targets.length + " attempts)");
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

	/**
	 * Stuck in a cage of cobwebs (too thick to just walk out of), the bot pours water at its feet to
	 * wash them away and scoops the water back up.
	 */
	private static void escapesCobwebWithWater(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.7 with minecraft:water_bucket");
		server.runCommand("fill -1 " + GROUND_Y + " -1 1 " + (GROUND_Y + 1) + " 1 minecraft:cobweb");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 8 {NoAI:1b,PersistenceRequired:1b}");
		context.waitTicks(5);

		setBot(context, true);
		int poured = waitOrExplain(context, "pour water", minecraft -> minecraft.player.getInventory().getItem(7).is(Items.BUCKET), 200);
		int scooped = waitOrExplain(context, "scoop the water back up",
			minecraft -> minecraft.player.getInventory().getItem(7).is(Items.WATER_BUCKET) && !inCobweb(minecraft), 200);
		setBot(context, false);
		server.runCommand("fill -2 " + GROUND_Y + " -2 2 " + (GROUND_Y + 2) + " 2 minecraft:air replace minecraft:cobweb");
		System.out.println("[qwantiumbot-gametest] poured water in the cobweb cage after " + poured + " ticks, free and scooped it back after " + scooped + " more");
	}

	private static boolean inCobweb(Minecraft minecraft) {
		return minecraft.level.getBlockState(minecraft.player.blockPosition()).is(Blocks.COBWEB)
			|| minecraft.level.getBlockState(BlockPos.containing(minecraft.player.getEyePosition())).is(Blocks.COBWEB);
	}

	/** After a hit knocks the opponent back and up, a cobweb lands under them. */
	private static void throwsCobwebAtKnockedBackOpponent(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		setUtilities(context, true, true, false);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.2 with minecraft:cobweb 64");
		server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		// The long fight against a tanky husk is about the item trick, not about surviving it.
		server.runCommand("effect give @p minecraft:resistance 60 4 true");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 6 {PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000}]}");
		context.waitTicks(5);

		setBot(context, true);
		int ticks = waitOrExplain(context, "throw a cobweb", minecraft -> countBlocks(minecraft, Blocks.COBWEB) > 0, 600);
		setBot(context, false);
		System.out.println("[qwantiumbot-gametest] threw a cobweb at the opponent after " + ticks + " ticks");
		server.runCommand("fill -40 " + GROUND_Y + " -40 40 " + (GROUND_Y + 3) + " 40 minecraft:air replace minecraft:cobweb");
		setUtilities(context, true, true, true);
	}

	/** An opponent knocked into the air gets lava under them; the bot picks it up again later. */
	private static void poursLavaUnderAirborneOpponent(ClientGameTestContext context, TestServerContext server) {
		resetArena(context, server);
		setUtilities(context, false, true, true);
		server.runCommand("item replace entity @p hotbar.0 with minecraft:diamond_sword");
		server.runCommand("item replace entity @p hotbar.8 with minecraft:lava_bucket");
		server.runCommand("item replace entity @p armor.chest with minecraft:diamond_chestplate");
		// The long fight against a tanky husk is about the item trick, not about surviving it.
		server.runCommand("effect give @p minecraft:resistance 60 4 true");
		server.runCommand("summon minecraft:husk 0 " + GROUND_Y + " 6 {PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000}]}");
		context.waitTicks(5);

		setBot(context, true);
		int ticks = waitOrExplain(context, "pour lava", minecraft -> countBlocks(minecraft, Blocks.LAVA) > 0, 600);
		// Like Quantum's bot it only scoops lava up again while it is in reach, so this is not required.
		int pickedUp = -1;
		for (int tick = 0; tick < 200 && pickedUp < 0; tick++) {
			context.waitTick();
			if (context.computeOnClient(minecraft -> minecraft.player.getInventory().getItem(8).is(Items.LAVA_BUCKET))) {
				pickedUp = tick;
			}
		}
		setBot(context, false);
		System.out.println("[qwantiumbot-gametest] poured lava under the opponent after " + ticks + " ticks, "
			+ (pickedUp >= 0 ? "picked it up after " + pickedUp + " more" : "fight moved away before picking it up"));
		server.runCommand("kill @e[type=minecraft:husk]");
		server.runCommand("fill -40 " + GROUND_Y + " -40 40 " + (GROUND_Y + 3) + " 40 minecraft:air replace minecraft:lava");
		setUtilities(context, true, true, true);
	}

	/** Waits like {@code waitFor}, but explains the bot's state when it times out. */
	private static int waitOrExplain(ClientGameTestContext context, String goal, java.util.function.Predicate<Minecraft> done, int timeout) {
		for (int tick = 0; tick <= timeout; tick++) {
			if (context.computeOnClient(done::test)) {
				return tick;
			}
			context.waitTick();
		}
		String state = context.computeOnClient(minecraft -> {
			StringBuilder blocks = new StringBuilder();
			BlockPos feet = minecraft.player.blockPosition();
			for (int dy = -1; dy <= 2; dy++) {
				blocks.append(dy).append('=').append(minecraft.level.getBlockState(feet.above(dy)).getBlock().getDescriptionId()).append(' ');
			}
			return String.format("pos %s rot %.0f/%.0f slot %d holding %s hit %s engaged %s blocks %s",
				minecraft.player.blockPosition().toShortString(), minecraft.player.getYRot(), minecraft.player.getXRot(),
				minecraft.player.getInventory().getSelectedSlot(), minecraft.player.getMainHandItem(), minecraft.hitResult == null ? null : minecraft.hitResult.getType(),
				BotController.get().isEngaged(), blocks);
		});
		context.takeScreenshot("qwantiumbot_timeout");
		throw new AssertionError("Bot did not " + goal + " within " + timeout + " ticks: " + state);
	}

	private static void setUtilities(ClientGameTestContext context, boolean cobwebs, boolean water, boolean lava) {
		context.runOnClient(minecraft -> {
			ClientConfig config = BotController.get().config();
			config.useCobwebs = cobwebs;
			config.useWater = water;
			config.useLava = lava;
		});
	}

	private static boolean blockIs(Minecraft minecraft, int x, int y, int z, Block block) {
		return minecraft.level.getBlockState(new BlockPos(x, y, z)).is(block);
	}

	/** Counts blocks of a kind around the player (the fight moves around the arena). */
	private static int countBlocks(Minecraft minecraft, Block block) {
		int count = 0;
		BlockPos center = minecraft.player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(center.getX() - 10, GROUND_Y, center.getZ() - 10, center.getX() + 10, GROUND_Y + 3, center.getZ() + 10)) {
			if (minecraft.level.getBlockState(pos).is(block)) {
				count++;
			}
		}
		return count;
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
