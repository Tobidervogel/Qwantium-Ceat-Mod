package dev.qwantium.pvpbot.mixin.client;

import dev.qwantium.pvpbot.client.bot.BotController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Turns the camera every frame while the bot fights, and ignores mouse movement meanwhile. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
	private void qwantium$aim(CallbackInfo ci) {
		BotController.get().onFrame(this.minecraft);
	}

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void qwantium$ignoreMouseWhileFighting(double movementTime, CallbackInfo ci) {
		if (BotController.get().isEngaged()) {
			ci.cancel();
		}
	}
}
