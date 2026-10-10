package dev.qwantium.pvpbot.gametest;

import dev.qwantium.pvpbot.client.ClientConfig;
import dev.qwantium.pvpbot.client.bot.BotController;
import dev.qwantium.pvpbot.client.bot.Difficulty;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * Optional: fights the real bot of Quantum's PvP Practice map. The map is not part of this
 * repository; point the test at your own unzipped copy with
 * {@code ./gradlew runClientGameTest -PquantumMap=/path/to/map}. Without it the scenario is skipped.
 */
final class QuantumMapDuel {
	static final String PROPERTY = "qwantiumbot.quantumMap";
	private static final String LEVEL_ID = "qwantiumbot_quantum_map";
	private static final String QUANTUM_BOT = "quantumbot";
	private static final int FIGHT_TICKS = 2400;

	private QuantumMapDuel() {
	}

	static void runIfConfigured(ClientGameTestContext context) {
		String mapPath = System.getProperty(PROPERTY, "");
		if (mapPath.isBlank()) {
			System.out.println("[qwantiumbot-gametest] Quantum map duel skipped (run with -PquantumMap=<map folder>)");
			return;
		}

		Path saves = context.computeOnClient(minecraft -> minecraft.getLevelSource().getBaseDir());
		copyWorld(Path.of(mapPath), saves.resolve(LEVEL_ID));
		context.runOnClient(minecraft -> minecraft.createWorldOpenFlows().openWorld(LEVEL_ID, () -> { }));
		context.waitFor(minecraft -> minecraft.level != null && minecraft.player != null && minecraft.screen == null, 12000);
		context.waitFor(minecraft -> findPlayer(minecraft, QUANTUM_BOT), 600);
		context.waitTicks(20);
		context.takeScreenshot("qwantiumbot_quantum_hub");

		String me = context.computeOnClient(minecraft -> minecraft.player.getScoreboardName());
		runCommand(context, "execute as " + me + " at @s run function quantum:map/start");
		context.waitFor(minecraft -> score(minecraft, ".start", "start") == 1, 400);
		// The map only equips its bot; give us the same diamond sword kit (gear setting 2).
		for (String slot : new String[] {"armor.head", "armor.chest", "armor.legs", "armor.feet"}) {
			String piece = switch (slot) {
				case "armor.head" -> "diamond_helmet";
				case "armor.chest" -> "diamond_chestplate";
				case "armor.legs" -> "diamond_leggings";
				default -> "diamond_boots";
			};
			runCommand(context, "item replace entity " + me + " " + slot + " with minecraft:" + piece);
		}
		runCommand(context, "item replace entity " + me + " hotbar.0 with minecraft:diamond_sword");
		context.takeScreenshot("qwantiumbot_quantum_start");

		context.runOnClient(minecraft -> {
			ClientConfig config = BotController.get().config();
			config.difficulty = Difficulty.QUANTUM;
			config.targetMode = TargetMode.PLAYER;
			config.targetName = QUANTUM_BOT;
			config.autoRespawn = true;
			BotController.get().setActive(minecraft, true);
		});

		int engagedTicks = 0;
		int hitsLanded = 0;
		int hitsTaken = 0;
		int ourDeaths = 0;
		int lastEnemyHurt = 0;
		int lastOwnHurt = 0;
		boolean wasDead = false;
		int startPops = score(context, QUANTUM_BOT, "pops");
		for (int tick = 1; tick <= FIGHT_TICKS; tick++) {
			context.waitTick();
			int[] state = context.computeOnClient(minecraft -> {
				Player enemy = player(minecraft, QUANTUM_BOT);
				return new int[] {
					BotController.get().isEngaged() ? 1 : 0,
					enemy == null ? 0 : enemy.hurtTime,
					minecraft.player.hurtTime,
					minecraft.player.isDeadOrDying() ? 1 : 0
				};
			});
			engagedTicks += state[0];
			if (state[1] > lastEnemyHurt) {
				hitsLanded++;
			}
			if (state[2] > lastOwnHurt) {
				hitsTaken++;
			}
			lastEnemyHurt = state[1];
			lastOwnHurt = state[2];
			boolean dead = state[3] == 1;
			if (dead && !wasDead) {
				ourDeaths++;
				// The map sends everyone back to the hub; start the next round once we are back.
				context.waitFor(minecraft -> minecraft.player != null && minecraft.player.isAlive() && minecraft.screen == null, 400);
				context.waitTicks(60);
				runCommand(context, "execute as " + me + " at @s run function quantum:map/start");
				context.waitFor(minecraft -> score(minecraft, ".start", "start") == 1, 400);
				runCommand(context, "item replace entity " + me + " hotbar.0 with minecraft:diamond_sword");
				dead = false;
			}
			wasDead = dead;
			if (tick == 60) {
				context.takeScreenshot("qwantiumbot_quantum_fight");
			}
		}

		int pops = score(context, QUANTUM_BOT, "pops") - startPops;
		context.runOnClient(minecraft -> BotController.get().setActive(minecraft, false));
		System.out.printf("[qwantiumbot-gametest] Quantum map duel (Master vs Master, %d s): hits landed %d, hits taken %d, "
				+ "quantumbot totem pops %d, our deaths %d, bot engaged %d of %d ticks%n",
			FIGHT_TICKS / 20, hitsLanded, hitsTaken, pops, ourDeaths, engagedTicks, FIGHT_TICKS);

		context.runOnClient(minecraft -> {
			minecraft.level.disconnect(Component.translatable("menu.savingLevel"));
			minecraft.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")), false);
		});
		context.waitFor(minecraft -> minecraft.level == null && minecraft.getSingleplayerServer() == null, 1200);
		context.setScreen(TitleScreen::new);

		if (engagedTicks == 0) {
			throw new AssertionError("The bot never engaged Quantum's bot in the real map");
		}
	}

	private static Player player(Minecraft minecraft, String name) {
		for (Player player : minecraft.level.players()) {
			if (player.getScoreboardName().equals(name)) {
				return player;
			}
		}
		return null;
	}

	private static boolean findPlayer(Minecraft minecraft, String name) {
		return player(minecraft, name) != null;
	}

	private static void runCommand(ClientGameTestContext context, String command) {
		context.runOnClient(minecraft -> {
			MinecraftServer server = minecraft.getSingleplayerServer();
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
		});
		context.waitTicks(2);
	}

	private static int score(ClientGameTestContext context, String holder, String objectiveName) {
		return context.computeOnClient(minecraft -> score(minecraft, holder, objectiveName));
	}

	/** Reads a score from the client's copy of the scoreboard (the server syncs it). */
	private static int score(Minecraft minecraft, String holder, String objectiveName) {
		MinecraftServer server = minecraft.getSingleplayerServer();
		Scoreboard scoreboard = server != null ? server.getScoreboard() : minecraft.level.getScoreboard();
		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return 0;
		}
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), objective);
		return info == null ? 0 : info.value();
	}

	private static void copyWorld(Path source, Path target) {
		try (Stream<Path> files = Files.walk(source)) {
			files.forEach(file -> {
				Path relative = source.relativize(file);
				if (relative.toString().startsWith("__MACOSX") || relative.endsWith("session.lock")) {
					return;
				}
				try {
					Path destination = target.resolve(relative.toString());
					if (Files.isDirectory(file)) {
						Files.createDirectories(destination);
					} else {
						Files.copy(file, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
