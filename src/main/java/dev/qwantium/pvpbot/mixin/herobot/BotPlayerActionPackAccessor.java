package dev.qwantium.pvpbot.mixin.herobot;

import hero.bane.herobot.bot.BotPlayerActionPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the movement HeroBot's action pack holds, so it can be played on a real player's client. */
@Mixin(value = BotPlayerActionPack.class, remap = false)
public interface BotPlayerActionPackAccessor {
	@Accessor("forward")
	float qwantium$forward();

	@Accessor("strafing")
	float qwantium$strafing();

	@Accessor("sneaking")
	boolean qwantium$sneaking();

	@Accessor("sprinting")
	boolean qwantium$sprinting();
}
