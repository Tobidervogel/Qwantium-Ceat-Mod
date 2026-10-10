package dev.qwantium.pvpbot.client.bot;

import dev.qwantium.pvpbot.client.ClientConfig;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/** Picks the opponent the bot fights. */
final class TargetFinder {
	/** Extra range a target keeps before the bot lets go of it. */
	private static final double KEEP_TARGET_SLACK = 8.0;

	private TargetFinder() {
	}

	static @Nullable LivingEntity find(LocalPlayer self, ClientLevel level, ClientConfig config, @Nullable LivingEntity current) {
		double range = config.searchRange;
		if (current != null && matchesMode(current, config) && isValid(self, current, range + KEEP_TARGET_SLACK, false)) {
			return current;
		}

		List<? extends LivingEntity> candidates = switch (config.targetMode) {
			case NEAREST_PLAYER, PLAYER -> level.players();
			case NEAREST_ANY -> level.getEntitiesOfClass(
				LivingEntity.class,
				self.getBoundingBox().inflate(range),
				entity -> entity instanceof Player || entity instanceof Enemy
			);
		};

		Optional<? extends LivingEntity> nearest = candidates.stream()
			.filter(entity -> matchesMode(entity, config))
			.filter(entity -> isValid(self, entity, range, true))
			.min(Comparator.comparingDouble(self::distanceToSqr));
		return nearest.orElse(null);
	}

	private static boolean matchesMode(LivingEntity entity, ClientConfig config) {
		return config.targetMode != TargetMode.PLAYER
			|| entity instanceof Player player && player.getScoreboardName().equalsIgnoreCase(config.targetName);
	}

	private static boolean isValid(LocalPlayer self, LivingEntity entity, double range, boolean needsSight) {
		if (entity == self || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof Player player && player.isCreative()) {
			return false;
		}
		// Teammates only count as allies when friendly fire is off. Quantum's map puts everyone,
		// bots included, into one team with friendly fire on.
		boolean ally = entity instanceof Player player ? !self.canHarmPlayer(player) : self.isAlliedTo(entity);
		if (ally || self.distanceToSqr(entity) > range * range) {
			return false;
		}
		return !needsSight || self.hasLineOfSight(entity);
	}
}
