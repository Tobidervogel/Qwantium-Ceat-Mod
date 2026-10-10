package dev.qwantium.pvpbot.client.bot;

import java.util.Locale;

/**
 * Skill profiles for the bot.
 *
 * @param aimSpeed max turn speed in degrees per second
 * @param aimGain how quickly the aim closes the gap to the target (higher = snappier)
 * @param aimJitter random wobble of the aim point in blocks
 * @param reactionTicks ticks a new target must be engaged before the first swing
 * @param minCharge lowest attack charge (0..1) the bot swings at
 * @param maxCharge highest attack charge (0..1) the bot waits for
 * @param wTapChance chance to reset sprint after a hit for extra knockback
 * @param strafeChance chance to strafe sideways while in melee range
 * @param critChance chance to go for a jump critical hit
 * @param shieldChance chance to block with an offhand shield between hits
 * @param eatHealth health (in half hearts) at which the bot eats a golden apple
 * @param predictTicks how far ahead the bot leads a moving target
 */
public enum Difficulty {
	EASY(220.0, 7.0, 0.22, 8, 0.70, 0.90, 0.0, 0.30, 0.00, 0.10, 6.0f, 0.0),
	NORMAL(420.0, 12.0, 0.14, 5, 0.88, 1.00, 0.40, 0.60, 0.15, 0.35, 8.0f, 1.0),
	HARD(650.0, 18.0, 0.08, 3, 0.95, 1.00, 0.80, 0.85, 0.30, 0.60, 10.0f, 1.5),
	QUANTUM(900.0, 26.0, 0.04, 1, 1.00, 1.00, 1.00, 1.00, 0.40, 0.80, 10.0f, 2.0);

	public final double aimSpeed;
	public final double aimGain;
	public final double aimJitter;
	public final int reactionTicks;
	public final double minCharge;
	public final double maxCharge;
	public final double wTapChance;
	public final double strafeChance;
	public final double critChance;
	public final double shieldChance;
	public final float eatHealth;
	public final double predictTicks;

	Difficulty(
		double aimSpeed,
		double aimGain,
		double aimJitter,
		int reactionTicks,
		double minCharge,
		double maxCharge,
		double wTapChance,
		double strafeChance,
		double critChance,
		double shieldChance,
		float eatHealth,
		double predictTicks
	) {
		this.aimSpeed = aimSpeed;
		this.aimGain = aimGain;
		this.aimJitter = aimJitter;
		this.reactionTicks = reactionTicks;
		this.minCharge = minCharge;
		this.maxCharge = maxCharge;
		this.wTapChance = wTapChance;
		this.strafeChance = strafeChance;
		this.critChance = critChance;
		this.shieldChance = shieldChance;
		this.eatHealth = eatHealth;
		this.predictTicks = predictTicks;
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
