package dev.qwantium.pvpbot.client.bot;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Replaces the player's keyboard input while the bot is on. When the bot has no opponent it passes
 * your own keyboard through, so you can still walk around; in a fight the bot's keys are used.
 */
public final class BotInput extends ClientInput {
	private final ClientInput keyboard;

	BotInput(ClientInput keyboard) {
		this.keyboard = keyboard;
	}

	ClientInput keyboard() {
		return this.keyboard;
	}

	@Override
	public void tick() {
		BotController controller = BotController.get();
		if (!controller.isEngaged()) {
			this.keyboard.tick();
			this.keyPresses = this.keyboard.keyPresses;
			this.moveVector = this.keyboard.getMoveVector();
			return;
		}

		this.keyPresses = controller.intent();
		float forward = impulse(this.keyPresses.forward(), this.keyPresses.backward());
		float left = impulse(this.keyPresses.left(), this.keyPresses.right());
		this.moveVector = new Vec2(left, forward).normalized();
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) {
			return 0.0F;
		}
		return positive ? 1.0F : -1.0F;
	}

	static Input keys(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sprint) {
		return new Input(forward, backward, left, right, jump, false, sprint);
	}
}
