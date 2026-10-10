package dev.qwantium.pvpbot.client;

import net.minecraft.client.Minecraft;

/**
 * Decides whether the bot may control the player in the current world.
 *
 * <p>Allowed in your own singleplayer/LAN world (the integrated server runs this mod), and on
 * servers that run this mod and sent {@link dev.qwantium.pvpbot.network.BotPermissionPayload}
 * with {@code allowed = true}. Everywhere else the bot stays off.
 */
public final class BotPermission {
	private static boolean serverAllowed;

	private BotPermission() {
	}

	public static boolean isAllowed(Minecraft minecraft) {
		return minecraft.hasSingleplayerServer() || serverAllowed;
	}

	static void setServerAllowed(boolean allowed) {
		serverAllowed = allowed;
	}
}
