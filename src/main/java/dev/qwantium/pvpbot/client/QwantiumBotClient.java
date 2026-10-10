package dev.qwantium.pvpbot.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.qwantium.pvpbot.QwantiumBotMod;
import dev.qwantium.pvpbot.client.bot.BotController;
import dev.qwantium.pvpbot.client.bot.TargetMode;
import dev.qwantium.pvpbot.network.BotPermissionPayload;
import dev.qwantium.pvpbot.network.QuantumControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import org.lwjgl.glfw.GLFW;

public class QwantiumBotClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "main"));

	private static KeyMapping toggleKey;
	private static KeyMapping lockTargetKey;
	private static KeyMapping difficultyKey;

	@Override
	public void onInitializeClient() {
		BotController controller = BotController.get();
		controller.setConfig(ClientConfig.load());

		toggleKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.qwantiumbot.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));
		lockTargetKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.qwantiumbot.lock_target", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY));
		difficultyKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.qwantiumbot.cycle_difficulty", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			QuantumRemote.tick();
			controller.tick(client);
		});
		ClientTickEvents.END_CLIENT_TICK.register(QwantiumBotClient::handleKeys);

		ClientPlayNetworking.registerGlobalReceiver(BotPermissionPayload.TYPE,
			(payload, context) -> BotPermission.setServerAllowed(payload.allowed()));
		ClientPlayNetworking.registerGlobalReceiver(QuantumControlPayload.TYPE, (payload, context) -> QuantumRemote.onPayload(payload));
		ClientPlayConnectionEvents.INIT.register((handler, client) -> BotPermission.setServerAllowed(false));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			BotPermission.setServerAllowed(false);
			QuantumRemote.reset();
			controller.forceOff(client);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> BotCommands.register(dispatcher));
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "status"), BotHud::render);
	}

	private static void handleKeys(Minecraft minecraft) {
		BotController controller = BotController.get();
		while (toggleKey.consumeClick()) {
			controller.toggle(minecraft);
		}

		while (lockTargetKey.consumeClick()) {
			lockTarget(minecraft, controller.config());
		}

		while (difficultyKey.consumeClick()) {
			ClientConfig config = controller.config();
			config.difficulty = config.difficulty.next();
			config.save();
			if (minecraft.player != null) {
				minecraft.player.displayClientMessage(Component.translatable("qwantiumbot.msg.difficulty",
					Component.translatable(config.difficulty.translationKey())).withStyle(ChatFormatting.AQUA), true);
			}
		}
	}

	/** Look at a player and press the key to fight only them; look at nothing to go back to "nearest". */
	private static void lockTarget(Minecraft minecraft, ClientConfig config) {
		if (minecraft.player == null) {
			return;
		}

		if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player) {
			config.targetMode = TargetMode.PLAYER;
			config.targetName = player.getScoreboardName();
		} else {
			config.targetMode = TargetMode.NEAREST_PLAYER;
		}
		config.save();
		minecraft.player.displayClientMessage(Component.translatable("qwantiumbot.msg.target",
			BotController.describeTarget(config)).withStyle(ChatFormatting.AQUA), true);
	}
}
