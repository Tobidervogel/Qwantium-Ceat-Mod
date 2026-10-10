package dev.qwantium.pvpbot.client.bot;

import java.util.Locale;

/**
 * Skill levels, modelled on the levels of Quantum's PvP Practice sword bot. The play style
 * (W/S-tap, strafing, crits, jump resets) is the same on every level; the levels differ in reach
 * and in how far the aim lags behind.
 *
 * @param reachBase lowest reach in tenths of a block; every hit rolls this plus a binomial(9, 1/2)
 *     bonus, capped at 3.0 blocks
 * @param aimBase slowest aim; every hit rolls a lag of 1..aimBase ticks to reach the target
 * @param eatHealth health (in half hearts) at which the bot eats a golden apple
 */
public enum Difficulty {
	EASY(13, 5, 6.0F),
	NORMAL(16, 4, 8.0F),
	HARD(20, 3, 10.0F),
	CRAZY(23, 2, 10.0F),
	QUANTUM(29, 2, 10.0F);

	public final int reachBase;
	public final int aimBase;
	public final float eatHealth;

	Difficulty(int reachBase, int aimBase, float eatHealth) {
		this.reachBase = reachBase;
		this.aimBase = aimBase;
		this.eatHealth = eatHealth;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String translationKey() {
		return "qwantiumbot.difficulty." + this.id();
	}

	public Difficulty next() {
		Difficulty[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
