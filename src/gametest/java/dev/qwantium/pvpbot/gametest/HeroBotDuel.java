package dev.qwantium.pvpbot.gametest;

import dev.qwantium.pvpbot.client.ClientConfig;
import dev.qwantium.pvpbot.client.bot.BotController;
import dev.qwantium.pvpbot.client.bot.Difficulty;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

/**
 * Duels against a HeroBot fake player, the same kind of bot Quantum's PvP Practice map uses.
 *
 * <p>The opponent is driven by a simple but sharp script: it tracks you perfectly every tick
 * (looks at the closest point of your hitbox), sprints at you and swings every 12 ticks. Both
 * sides get the same kit (diamond sword, full iron armor) and, as in Quantum's map, share one team
 * with friendly fire on.
 */
final class HeroBotDuel {
	static final String OPPONENT = "Sparring";
	private static final String DEATHS = "qbot_deaths";
	private static final int GROUND_Y = -60;
	private static final int MAX_ROUND_TICKS = 600;

	private final ClientGameTestContext context;
	private final TestServerContext server;
	private final String me;

	HeroBotDuel(ClientGameTestContext context, TestServerContext server) {
		this.context = context;
		this.server = server;
		this.me = context.computeOnClient(minecraft -> minecraft.player.getScoreboardName());
		server.runCommand("scoreboard objectives add " + DEATHS + " deathCount");
		server.runCommand("playerspawn " + OPPONENT + " at 0 " + GROUND_Y + " 7 facing north in survival");
		context.waitTicks(20);
		// Like Quantum's map: everyone, bots included, in one team with friendly fire on.
		server.runCommand("team add qbot_rtp");
		server.runCommand("team join qbot_rtp @a");
	}

	record Result(int wins, int losses, int draws) {
	}

	Result play(Difficulty difficulty, int rounds) {
		this.context.runOnClient(minecraft -> {
			ClientConfig config = BotController.get().config();
			config.difficulty = difficulty;
			config.targetMode = TargetMode.PLAYER;
			config.targetName = OPPONENT;
		});

		int wins = 0;
		int losses = 0;
		int draws = 0;
		for (int round = 1; round <= rounds; round++) {
			this.resetRound();
			int myDeaths = this.deaths(this.me);
			int theirDeaths = this.deaths(OPPONENT);
			this.context.runOnClient(minecraft -> BotController.get().setActive(minecraft, true));

			String outcome = "draw";
			int tick = 0;
			for (; tick < MAX_ROUND_TICKS; tick++) {
				this.server.runCommand("player " + OPPONENT + " look upon " + this.me + " closest");
				this.context.waitTick();
				if (this.deaths(OPPONENT) > theirDeaths) {
					outcome = "WIN";
					wins++;
					break;
				}
				if (this.deaths(this.me) > myDeaths) {
					outcome = "LOSS";
					losses++;
					break;
				}
			}
			if ("draw".equals(outcome)) {
				draws++;
			}

			float health = this.context.computeOnClient(minecraft -> minecraft.player.getHealth());
			this.context.runOnClient(minecraft -> BotController.get().setActive(minecraft, false));
			this.stopOpponent();
			System.out.printf("[qwantiumbot-gametest] duel %s round %d: %s after %d ticks, our health %.1f%n",
				difficulty.id(), round, outcome, tick, health);
			this.respawnIfDead();
		}

		System.out.printf("[qwantiumbot-gametest] duel %s vs HeroBot script: %d wins, %d losses, %d draws%n",
			difficulty.id(), wins, losses, draws);
		return new Result(wins, losses, draws);
	}

	void remove() {
		this.server.runCommand("herobot botLeaveOnDeath true");
		this.server.runCommand("kill " + OPPONENT);
		this.context.waitTicks(5);
	}

	private void resetRound() {
		for (String name : new String[] {this.me, OPPONENT}) {
			this.server.runCommand("clear " + name);
			this.server.runCommand("effect clear " + name);
			this.server.runCommand("effect give " + name + " minecraft:instant_health 1 10 true");
			this.server.runCommand("effect give " + name + " minecraft:saturation 1 10 true");
			this.server.runCommand("item replace entity " + name + " hotbar.0 with minecraft:diamond_sword");
			this.server.runCommand("item replace entity " + name + " armor.head with minecraft:iron_helmet");
			this.server.runCommand("item replace entity " + name + " armor.chest with minecraft:iron_chestplate");
			this.server.runCommand("item replace entity " + name + " armor.legs with minecraft:iron_leggings");
			this.server.runCommand("item replace entity " + name + " armor.feet with minecraft:iron_boots");
		}
		this.server.runCommand("tp " + this.me + " 0 " + GROUND_Y + " 0 facing 0 " + GROUND_Y + " 8");
		this.server.runCommand("tp " + OPPONENT + " 0 " + GROUND_Y + " 8 facing 0 " + GROUND_Y + " 0");
		this.context.waitTicks(10);
		this.server.runCommand("player " + OPPONENT + " move forward");
		this.server.runCommand("player " + OPPONENT + " sprint");
		this.server.runCommand("player " + OPPONENT + " attack interval 12");
	}

	private void stopOpponent() {
		this.server.runCommand("player " + OPPONENT + " move");
		this.server.runCommand("player " + OPPONENT + " unsprint");
		this.server.runCommand("player " + OPPONENT + " attack");
	}

	private void respawnIfDead() {
		this.context.runOnClient(minecraft -> {
			if (minecraft.player.isDeadOrDying()) {
				minecraft.player.respawn();
				minecraft.setScreen(null);
			}
		});
		this.context.waitFor(minecraft -> minecraft.player != null && minecraft.player.isAlive() && minecraft.screen == null, 200);
		this.context.waitTicks(10);
	}

	private int deaths(String name) {
		return this.server.computeOnServer(minecraftServer -> {
			Scoreboard scoreboard = minecraftServer.getScoreboard();
			Objective objective = scoreboard.getObjective(DEATHS);
			ReadOnlyScoreInfo score = objective == null ? null : scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective);
			return score == null ? 0 : score.value();
		});
	}
}
