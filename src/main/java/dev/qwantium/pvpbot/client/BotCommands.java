package dev.qwantium.pvpbot.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.qwantium.pvpbot.client.bot.BotController;
import dev.qwantium.pvpbot.client.bot.Difficulty;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Client-side {@code /qbot} command. */
final class BotCommands {
	private BotCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("qbot")
			.executes(context -> status(context.getSource()))
			.then(literal("on").executes(context -> setActive(context.getSource(), true)))
			.then(literal("off").executes(context -> setActive(context.getSource(), false)))
			.then(literal("toggle").executes(context -> {
				BotController.get().toggle(context.getSource().getClient());
				return Command.SINGLE_SUCCESS;
			}))
			.then(literal("status").executes(context -> status(context.getSource())))
			.then(literal("help").executes(context -> help(context.getSource())))
			.then(literal("difficulty")
				.then(argument("level", StringArgumentType.word())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(
						Arrays.stream(Difficulty.values()).map(Difficulty::id), builder))
					.executes(BotCommands::setDifficulty)))
			.then(literal("target")
				.then(literal("nearest").executes(context -> setTarget(context.getSource(), TargetMode.NEAREST_PLAYER, "")))
				.then(literal("mobs").executes(context -> setTarget(context.getSource(), TargetMode.NEAREST_ANY, "")))
				.then(argument("player", StringArgumentType.word())
					.suggests((context, builder) -> SharedSuggestionProvider.suggest(
						context.getSource().getOnlinePlayerNames(), builder))
					.executes(context -> setTarget(context.getSource(), TargetMode.PLAYER,
						StringArgumentType.getString(context, "player")))))
			.then(literal("quantum")
				.then(literal("on").executes(context -> quantum(context.getSource(), true)))
				.then(literal("off").executes(context -> quantum(context.getSource(), false))))
			.then(literal("utility")
				.then(utilityToggle("cobweb"))
				.then(utilityToggle("water"))
				.then(utilityToggle("lava")))
			.then(literal("autorespawn")
				.then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
					ClientConfig config = config();
					config.autoRespawn = BoolArgumentType.getBool(context, "enabled");
					config.save();
					context.getSource().sendFeedback(Component.translatable("qwantiumbot.msg.autorespawn",
						config.autoRespawn ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
					return Command.SINGLE_SUCCESS;
				})))
			.then(literal("range")
				.then(argument("blocks", IntegerArgumentType.integer(4, 64)).executes(context -> {
					ClientConfig config = config();
					config.searchRange = IntegerArgumentType.getInteger(context, "blocks");
					config.save();
					context.getSource().sendFeedback(Component.translatable("qwantiumbot.msg.range", (int) config.searchRange));
					return Command.SINGLE_SUCCESS;
				}))));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> utilityToggle(String name) {
		return literal(name).then(argument("enabled", BoolArgumentType.bool()).executes(context -> {
			ClientConfig config = config();
			boolean enabled = BoolArgumentType.getBool(context, "enabled");
			switch (name) {
				case "cobweb" -> config.useCobwebs = enabled;
				case "water" -> config.useWater = enabled;
				default -> config.useLava = enabled;
			}
			config.save();
			context.getSource().sendFeedback(Component.translatable("qwantiumbot.msg.utility",
				Component.translatable("qwantiumbot.utility." + name), enabled ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF));
			return Command.SINGLE_SUCCESS;
		}));
	}

	private static ClientConfig config() {
		return BotController.get().config();
	}

	private static int setActive(FabricClientCommandSource source, boolean active) {
		BotController.get().setActive(source.getClient(), active);
		return Command.SINGLE_SUCCESS;
	}

	private static int quantum(FabricClientCommandSource source, boolean enable) {
		if (enable && !BotPermission.isAllowed(source.getClient())) {
			source.sendError(Component.translatable("qwantiumbot.msg.not_allowed"));
			return 0;
		}
		if (!QuantumRemote.canRequest()) {
			source.sendError(Component.translatable("qwantiumbot.quantum.no_server_mod"));
			return 0;
		}
		if (enable && BotController.get().isActive()) {
			// Quantum's bot takes over; our own engine steps aside.
			BotController.get().setActive(source.getClient(), false);
		}
		QuantumRemote.request(enable);
		source.sendFeedback(Component.translatable(enable ? "qwantiumbot.quantum.requested" : "qwantiumbot.quantum.stopping"));
		return Command.SINGLE_SUCCESS;
	}

	private static int setDifficulty(CommandContext<FabricClientCommandSource> context) {
		String id = StringArgumentType.getString(context, "level").toLowerCase(Locale.ROOT);
		for (Difficulty difficulty : Difficulty.values()) {
			if (difficulty.id().equals(id)) {
				ClientConfig config = config();
				config.difficulty = difficulty;
				config.save();
				context.getSource().sendFeedback(Component.translatable("qwantiumbot.msg.difficulty",
					Component.translatable(difficulty.translationKey())));
				return Command.SINGLE_SUCCESS;
			}
		}

		context.getSource().sendError(Component.translatable("qwantiumbot.msg.unknown_difficulty", id));
		return 0;
	}

	private static int setTarget(FabricClientCommandSource source, TargetMode mode, String name) {
		ClientConfig config = config();
		config.targetMode = mode;
		config.targetName = name;
		config.save();
		source.sendFeedback(Component.translatable("qwantiumbot.msg.target", BotController.describeTarget(config)));
		return Command.SINGLE_SUCCESS;
	}

	private static int status(FabricClientCommandSource source) {
		BotController controller = BotController.get();
		ClientConfig config = controller.config();
		boolean allowed = BotPermission.isAllowed(source.getClient());
		source.sendFeedback(Component.translatable("qwantiumbot.msg.status",
			controller.isActive()
				? Component.translatable("qwantiumbot.status.on").withStyle(ChatFormatting.GREEN)
				: Component.translatable("qwantiumbot.status.off").withStyle(ChatFormatting.GRAY),
			Component.translatable(config.difficulty.translationKey()),
			BotController.describeTarget(config),
			allowed
				? Component.translatable("qwantiumbot.status.allowed").withStyle(ChatFormatting.GREEN)
				: Component.translatable("qwantiumbot.status.blocked").withStyle(ChatFormatting.RED)));
		return Command.SINGLE_SUCCESS;
	}

	private static int help(FabricClientCommandSource source) {
		source.sendFeedback(Component.translatable("qwantiumbot.help"));
		return Command.SINGLE_SUCCESS;
	}
}
