package dev.qwantium.pvpbot.quantum;

import dev.qwantium.pvpbot.QwantiumBotMod;
import dev.qwantium.pvpbot.ServerConfig;
import dev.qwantium.pvpbot.network.QuantumControlPayload;
import dev.qwantium.pvpbot.network.QuantumControlRequestPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Lets the bot of Quantum's PvP Practice map play a real player.
 *
 * <p>A player turns it on for themselves with {@code /qbot quantum on}. From then on:
 * <ul>
 *   <li>the built-in {@code quantum_bridge} datapack makes Quantum's map treat that player as its bot
 *       and everyone else as the opponent,</li>
 *   <li>HeroBot accepts Quantum's {@code /player} commands for that player (see
 *       {@code PlayerCommandMixin}); attacks, item use and hotbar changes happen on the server,</li>
 *   <li>every tick the movement, sprint, sneak, jumps and camera HeroBot chose are sent to the
 *       player's client, which plays them as its own keys.</li>
 * </ul>
 * The client only follows these inputs after its own user asked for it, and it stops when the player
 * leaves or types {@code /qbot quantum off}.
 */
public final class QuantumBridge {
	public static final String CONTROL_TAG = "qbot_quantum";
	/** The fake player Quantum's map runs its bot on. */
	private static final String MAP_BOT = "quantumbot";
	/** Fake players Quantum's map can spawn as bots. */
	private static final Set<String> MAP_BOTS = Set.of(MAP_BOT, "Quantum", "Notch", "Herobrine");
	private static final Identifier PACK_ID = Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "quantum_bridge");

	private static final Set<UUID> JUMPED = new HashSet<>();
	private static boolean heroBotLoaded;
	private static ServerConfig config;

	private QuantumBridge() {
	}

	public static void init(ServerConfig serverConfig) {
		config = serverConfig;
		heroBotLoaded = FabricLoader.getInstance().isModLoaded("herobot");
		FabricLoader.getInstance().getModContainer(QwantiumBotMod.MOD_ID).ifPresent(mod -> ResourceLoader.registerBuiltinPack(
			PACK_ID, mod, Component.literal("Qwantium PvP Bot: Quantum bridge"), PackActivationType.NORMAL));

		ServerPlayNetworking.registerGlobalReceiver(QuantumControlRequestPayload.TYPE,
			(payload, context) -> setControlled(context.player(), payload.enable()));
		ServerTickEvents.END_SERVER_TICK.register(QuantumBridge::sendInputs);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			handler.player.removeTag(CONTROL_TAG);
			JUMPED.remove(handler.player.getUUID());
		});
	}

	public static boolean isControlled(ServerPlayer player) {
		return player.getTags().contains(CONTROL_TAG);
	}

	public static void markJump(ServerPlayer player) {
		JUMPED.add(player.getUUID());
	}

	private static void setControlled(ServerPlayer player, boolean enable) {
		if (!enable) {
			if (player.removeTag(CONTROL_TAG)) {
				if (heroBotLoaded) {
					HeroBotCompat.stopAll(player);
				}
				ServerPlayNetworking.send(player, QuantumControlPayload.off());
			}
			player.displayClientMessage(Component.translatable("qwantiumbot.quantum.off").withStyle(ChatFormatting.YELLOW), false);
			return;
		}

		if (!config.allowBot()) {
			player.displayClientMessage(Component.translatable("qwantiumbot.msg.not_allowed").withStyle(ChatFormatting.RED), false);
			return;
		}
		if (!heroBotLoaded) {
			player.displayClientMessage(Component.translatable("qwantiumbot.quantum.no_herobot").withStyle(ChatFormatting.RED), false);
			return;
		}

		// Quantum's map needs someone to fight; alone it would end every round at once.
		boolean opponentPresent = player.level().getServer().getPlayerList().getPlayers().stream()
			.anyMatch(other -> other != player && !other.isSpectator() && !MAP_BOTS.contains(other.getScoreboardName()));
		if (!opponentPresent) {
			player.displayClientMessage(Component.translatable("qwantiumbot.quantum.no_opponent").withStyle(ChatFormatting.RED), false);
			return;
		}

		player.addTag(CONTROL_TAG);
		inheritBotScores(player);
		player.displayClientMessage(Component.translatable("qwantiumbot.quantum.on").withStyle(ChatFormatting.GREEN), false);
		if (enableBridgePack(player.level().getServer())) {
			player.displayClientMessage(Component.translatable("qwantiumbot.quantum.pack_enabled").withStyle(ChatFormatting.GRAY), false);
		}
	}

	/**
	 * Quantum's datapack only runs parts of its logic (aiming, for one) when the bot has its scores
	 * set up, such as reach, aim, cooldowns and item timers. The player taking over the bot gets every
	 * score the map's bot has and they lack; scores they already have stay as they are.
	 */
	private static void inheritBotScores(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		ServerPlayer mapBot = server.getPlayerList().getPlayerByName(MAP_BOT);
		if (mapBot == null) {
			return;
		}

		Scoreboard scoreboard = server.getScoreboard();
		int copied = 0;
		for (Objective objective : scoreboard.getObjectives()) {
			ReadOnlyScoreInfo botScore = scoreboard.getPlayerScoreInfo(mapBot, objective);
			if (botScore != null && scoreboard.getPlayerScoreInfo(player, objective) == null) {
				scoreboard.getOrCreatePlayerScore(player, objective).set(botScore.value());
				copied++;
			}
		}
		QwantiumBotMod.LOGGER.info("Quantum control: {} took over {} scores from {}", player.getScoreboardName(), copied, MAP_BOT);
	}

	/** Makes sure the bridge datapack is on and loaded last, so it overrides Quantum's tag function. */
	private static boolean enableBridgePack(MinecraftServer server) {
		PackRepository repository = server.getPackRepository();
		Optional<String> packId = repository.getAvailableIds().stream().filter(id -> id.contains(PACK_ID.getPath())).findFirst();
		if (packId.isEmpty()) {
			QwantiumBotMod.LOGGER.warn("Quantum bridge datapack not found");
			return false;
		}

		List<String> selected = new ArrayList<>(repository.getSelectedIds());
		if (!selected.isEmpty() && selected.getLast().equals(packId.get())) {
			return false;
		}
		selected.remove(packId.get());
		selected.add(packId.get());
		server.reloadResources(selected).exceptionally(error -> {
			QwantiumBotMod.LOGGER.warn("Could not enable the Quantum bridge datapack", error);
			return null;
		});
		return true;
	}

	private static void sendInputs(MinecraftServer server) {
		if (!heroBotLoaded) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isControlled(player) || !ServerPlayNetworking.canSend(player, QuantumControlPayload.TYPE)) {
				continue;
			}

			HeroBotCompat.Inputs inputs = HeroBotCompat.read(player);
			boolean jumped = JUMPED.remove(player.getUUID());
			ServerPlayNetworking.send(player, new QuantumControlPayload(
				true,
				inputs.forward(),
				inputs.strafe(),
				jumped,
				inputs.sneak(),
				inputs.sprint(),
				player.getYRot(),
				player.getXRot(),
				player.getInventory().getSelectedSlot()
			));
		}
	}
}
