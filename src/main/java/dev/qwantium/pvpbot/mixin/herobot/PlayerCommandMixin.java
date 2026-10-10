package dev.qwantium.pvpbot.mixin.herobot;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.qwantium.pvpbot.quantum.QuantumBridge;
import hero.bane.herobot.bot.BotPlayer;
import hero.bane.herobot.bot.BotPlayerActionPack;
import hero.bane.herobot.bot.connection.ServerPlayerInterface;
import hero.bane.herobot.command.PlayerCommand;
import java.util.Collection;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HeroBot's {@code /player <targets> ...} actions (move, look at, sprint, jump, attack, use, hotbar,
 * stop, ...) only accept HeroBot's fake players. A real player who turned on Quantum control
 * themselves is accepted as well, so Quantum's datapack can drive them. Everything else
 * (disconnect, kill, ...) still refuses real players.
 */
@Mixin(value = PlayerCommand.class, remap = false)
public abstract class PlayerCommandMixin {
	@Inject(method = "manipulate", at = @At("HEAD"), cancellable = true)
	private static void qwantium$acceptControlledPlayers(
		CommandContext<CommandSourceStack> context,
		Consumer<BotPlayerActionPack> action,
		CallbackInfoReturnable<Integer> cir
	) {
		Collection<ServerPlayer> targets;
		try {
			targets = EntityArgument.getPlayers(context, "targets");
		} catch (CommandSyntaxException e) {
			return;
		}

		if (targets.stream().noneMatch(player -> !(player instanceof BotPlayer) && QuantumBridge.isControlled(player))) {
			return;
		}

		int count = 0;
		for (ServerPlayer player : targets) {
			if (player instanceof BotPlayer || QuantumBridge.isControlled(player)) {
				action.accept(((ServerPlayerInterface) player).getActionPack());
				count++;
			}
		}
		cir.setReturnValue(count);
	}
}
