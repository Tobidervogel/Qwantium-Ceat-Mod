package dev.qwantium.pvpbot.network;

import dev.qwantium.pvpbot.QwantiumBotMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent every tick to a player under Quantum control: the inputs Quantum's datapack chose for them
 * through HeroBot, which the client then plays as its own keys and camera.
 *
 * @param active false once Quantum control was switched off
 * @param forward 1 forward, -1 backward, 0 standing
 * @param strafe 1 left, -1 right
 * @param jump a jump happened this tick
 */
public record QuantumControlPayload(
	boolean active,
	float forward,
	float strafe,
	boolean jump,
	boolean sneak,
	boolean sprint,
	float yaw,
	float pitch,
	int slot
) implements CustomPacketPayload {
	public static final Type<QuantumControlPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(QwantiumBotMod.MOD_ID, "quantum_control"));
	public static final StreamCodec<ByteBuf, QuantumControlPayload> CODEC = StreamCodec.of(
		(buf, payload) -> {
			FriendlyByteBuf out = new FriendlyByteBuf(buf);
			out.writeBoolean(payload.active);
			out.writeFloat(payload.forward);
			out.writeFloat(payload.strafe);
			out.writeBoolean(payload.jump);
			out.writeBoolean(payload.sneak);
			out.writeBoolean(payload.sprint);
			out.writeFloat(payload.yaw);
			out.writeFloat(payload.pitch);
			out.writeVarInt(payload.slot);
		},
		buf -> {
			FriendlyByteBuf in = new FriendlyByteBuf(buf);
			return new QuantumControlPayload(in.readBoolean(), in.readFloat(), in.readFloat(), in.readBoolean(),
				in.readBoolean(), in.readBoolean(), in.readFloat(), in.readFloat(), in.readVarInt());
		}
	);

	public static QuantumControlPayload off() {
		return new QuantumControlPayload(false, 0.0F, 0.0F, false, false, false, 0.0F, 0.0F, 0);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
