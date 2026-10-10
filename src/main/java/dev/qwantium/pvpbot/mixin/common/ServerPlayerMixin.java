package dev.qwantium.pvpbot.mixin.common;

import dev.qwantium.pvpbot.quantum.QuantumBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Notices when HeroBot makes a player under Quantum control jump, so their client jumps too. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = "jumpFromGround", at = @At("HEAD"))
	private void qwantium$forwardJump(CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (QuantumBridge.isControlled(self)) {
			QuantumBridge.markJump(self);
		}
	}
}
