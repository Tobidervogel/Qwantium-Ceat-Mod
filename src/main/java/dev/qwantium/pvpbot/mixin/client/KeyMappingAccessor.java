package dev.qwantium.pvpbot.mixin.client;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the bot press keys the same way the keyboard and mouse do. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int qwantium$getClickCount();

	@Accessor("clickCount")
	void qwantium$setClickCount(int clickCount);

	@Accessor("isDown")
	void qwantium$setDown(boolean down);
}
