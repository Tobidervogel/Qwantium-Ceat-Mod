package dev.qwantium.pvpbot.client.bot;

/** Who the bot fights. */
public enum TargetMode {
	/** The closest player who is not on your team. */
	NEAREST_PLAYER,
	/** Only the player named in the config, e.g. your training partner. */
	PLAYER,
	/** The closest player or hostile mob (handy for testing alone). */
	NEAREST_ANY
}
