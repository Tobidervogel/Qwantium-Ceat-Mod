package dev.qwantium.pvpbot.network;

import dev.qwantium.pvpbot.QwantiumBotMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by the client ({@code /qbot quantum on|off}) to put its own player under Quantum control. */
public record QuantumControlRequestPayload(boolean enable) implements CustomPacketPayload {
	public static final Type<QuantumControlRequestPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "quantum_control_request"));
	public static final StreamCodec<ByteBuf, QuantumControlRequestPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.BOOL, QuantumControlRequestPayload::enable, QuantumControlRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
