package dev.qwantium.pvpbot.client;

import dev.qwantium.pvpbot.client.bot.BotController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Small status display in the top left corner while the bot is on. */
final class BotHud {
	private static final int GREEN = 0xFF55FF55;
	private static final int YELLOW = 0xFFFFFF55;
	private static final int GRAY = 0xFFAAAAAA;

	private BotHud() {
	}

	static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		BotController controller = BotController.get();
		if (!controller.isActive() || minecraft.player == null || minecraft.options.hideGui) {
			return;
		}

		Font font = minecraft.font;
		int x = 4;
		int y = 4;
		LivingEntity target = controller.target();
		if (target != null) {
			graphics.drawString(font, Component.translatable("qwantiumbot.hud.fighting"), x, y, GREEN, true);
			y += font.lineHeight + 1;
			String distance = String.format("%.1f", minecraft.player.distanceTo(target));
			graphics.drawString(font, Component.translatable("qwantiumbot.hud.target", target.getDisplayName(), distance), x, y, GRAY, true);
		} else {
			graphics.drawString(font, Component.translatable("qwantiumbot.hud.waiting"), x, y, YELLOW, true);
			y += font.lineHeight + 1;
			graphics.drawString(font, BotController.describeTarget(controller.config()), x, y, GRAY, true);
		}
		y += font.lineHeight + 1;
		graphics.drawString(font, Component.translatable("qwantiumbot.hud.difficulty",
			Component.translatable(controller.config().difficulty.translationKey())), x, y, GRAY, true);
	}
}
