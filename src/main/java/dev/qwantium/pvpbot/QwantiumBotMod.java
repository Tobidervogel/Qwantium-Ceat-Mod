package dev.qwantium.pvpbot;

import dev.qwantium.pvpbot.network.BotPermissionPayload;
import dev.qwantium.pvpbot.network.QuantumControlPayload;
import dev.qwantium.pvpbot.network.QuantumControlRequestPayload;
import dev.qwantium.pvpbot.quantum.QuantumBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Runs on dedicated servers and on the integrated (singleplayer/LAN) server.
 *
 * <p>The server half of the mod is what makes the bot usable for other players: when a player
 * joins, the server tells their client whether the bot is allowed here. A client only enables the
 * bot in its own singleplayer/LAN world or on a server that sent this permission, so the bot
 * cannot be used on servers that never agreed to it.
 */
public class QwantiumBotMod implements ModInitializer {
	public static final String MOD_ID = "qwantiumbot";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playS2C().register(BotPermissionPayload.TYPE, BotPermissionPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(QuantumControlPayload.TYPE, QuantumControlPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(QuantumControlRequestPayload.TYPE, QuantumControlRequestPayload.CODEC);

		ServerConfig config = ServerConfig.load();
		LOGGER.info("Qwantium PvP Bot: bot for joining players is {}", config.allowBot() ? "allowed" : "disabled");

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (ServerPlayNetworking.canSend(handler, BotPermissionPayload.TYPE)) {
				sender.sendPacket(new BotPermissionPayload(config.allowBot()));
			}
		});
		QuantumBridge.init(config);
	}
}
