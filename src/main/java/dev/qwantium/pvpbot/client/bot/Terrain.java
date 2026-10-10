package dev.qwantium.pvpbot.client.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps the bot from walking into lava, fire or off cliffs while it chases or strafes. */
final class Terrain {
	private static final double[] PROBE_DISTANCES = {0.6, 1.2};
	private static final int MAX_SAFE_DROP = 3;

	private Terrain() {
	}

	/** Whether moving in the given horizontal direction (unit vector) would be dangerous. */
	static boolean isUnsafe(Level level, Entity entity, double dirX, double dirZ) {
		for (double distance : PROBE_DISTANCES) {
			BlockPos feet = BlockPos.containing(entity.getX() + dirX * distance, entity.getY() + 0.01, entity.getZ() + dirZ * distance);
			if (isHazard(level, feet) || isHazard(level, feet.above())) {
				return true;
			}

			if (entity.onGround() && !isSolid(level, feet) && !hasSafeGroundBelow(level, feet)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasSafeGroundBelow(Level level, BlockPos feet) {
		for (int dy = 1; dy <= MAX_SAFE_DROP; dy++) {
			BlockPos pos = feet.below(dy);
			if (isHazard(level, pos)) {
				return false;
			}
			if (isSolid(level, pos) || level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSolid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	private static boolean isHazard(Level level, BlockPos pos) {
		if (level.getFluidState(pos).is(FluidTags.LAVA)) {
			return true;
		}

		BlockState state = level.getBlockState(pos);
		return state.is(BlockTags.FIRE)
			|| state.is(BlockTags.CAMPFIRES)
			|| state.is(Blocks.MAGMA_BLOCK)
			|| state.is(Blocks.CACTUS)
			|| state.is(Blocks.SWEET_BERRY_BUSH)
			|| state.is(Blocks.POWDER_SNOW)
			|| state.is(Blocks.WITHER_ROSE);
	}
}
