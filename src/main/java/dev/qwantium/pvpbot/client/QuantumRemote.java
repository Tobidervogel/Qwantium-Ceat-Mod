package dev.qwantium.pvpbot.client;

import dev.qwantium.pvpbot.network.QuantumControlPayload;
import dev.qwantium.pvpbot.network.QuantumControlRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client half of the Quantum bridge: holds the inputs Quantum's bot chose for this player.
 *
 * <p>Inputs are only followed after the user turned Quantum control on themselves with
 * {@code /qbot quantum on}, only where the bot is allowed ({@link BotPermission}) and only while the
 * server keeps sending them. Leaving the world turns it off.
 */
public final class QuantumRemote {
	/** Inputs older than this many ticks are dropped and the player gets their keyboard back. */
	private static final int STALE_TICKS = 5;

	private static boolean requested;
	private static @Nullable QuantumControlPayload latest;
	private static long latestTick;
	private static long ticks;
	private static boolean jumpPending;

	private QuantumRemote() {
	}

	public static void request(boolean enable) {
		requested = enable;
		if (!enable) {
			latest = null;
			jumpPending = false;
		}
		if (ClientPlayNetworking.canSend(QuantumControlRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new QuantumControlRequestPayload(enable));
		}
	}

	public static boolean isRequested() {
		return requested;
	}

	static boolean canRequest() {
		return ClientPlayNetworking.canSend(QuantumControlRequestPayload.TYPE);
	}

	static void onPayload(QuantumControlPayload payload) {
		if (!requested) {
			return;
		}
		if (!payload.active()) {
			latest = null;
			return;
		}
		latest = payload;
		latestTick = ticks;
		jumpPending |= payload.jump();
	}

	static void tick() {
		ticks++;
	}

	static void reset() {
		requested = false;
		latest = null;
		jumpPending = false;
	}

	public static boolean isActive(Minecraft minecraft) {
		return requested
			&& latest != null
			&& ticks - latestTick <= STALE_TICKS
			&& minecraft.player != null
			&& BotPermission.isAllowed(minecraft);
	}

	public static @Nullable QuantumControlPayload latest() {
		return latest;
	}

	public static boolean consumeJump() {
		boolean jump = jumpPending;
		jumpPending = false;
		return jump;
	}
}
