package dev.qwantium.pvpbot.network;

import dev.qwantium.pvpbot.QwantiumBotMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by a server running this mod to tell the client whether the PvP bot may be used. */
public record BotPermissionPayload(boolean allowed) implements CustomPacketPayload {
	public static final Type<BotPermissionPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "permission"));
	public static final StreamCodec<ByteBuf, BotPermissionPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, BotPermissionPayload::allowed, BotPermissionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
