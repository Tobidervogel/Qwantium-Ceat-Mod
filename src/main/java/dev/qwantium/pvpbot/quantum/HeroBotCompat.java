package dev.qwantium.pvpbot.quantum;

import dev.qwantium.pvpbot.mixin.herobot.BotPlayerActionPackAccessor;
import hero.bane.herobot.bot.BotPlayerActionPack;
import hero.bane.herobot.bot.connection.ServerPlayerInterface;
import net.minecraft.server.level.ServerPlayer;

/** Everything that touches HeroBot's classes. Only used when HeroBot is installed. */
final class HeroBotCompat {
	record Inputs(float forward, float strafe, boolean sneak, boolean sprint) {
	}

	private HeroBotCompat() {
	}

	static Inputs read(ServerPlayer player) {
		BotPlayerActionPackAccessor pack = (BotPlayerActionPackAccessor) actionPack(player);
		return new Inputs(pack.qwantium$forward(), pack.qwantium$strafing(), pack.qwantium$sneaking(), pack.qwantium$sprinting());
	}

	static void stopAll(ServerPlayer player) {
		actionPack(player).stopAll();
	}

	private static BotPlayerActionPack actionPack(ServerPlayer player) {
		return ((ServerPlayerInterface) player).getActionPack();
	}
}
