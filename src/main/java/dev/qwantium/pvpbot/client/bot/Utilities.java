package dev.qwantium.pvpbot.client.bot;

import dev.qwantium.pvpbot.client.ClientConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The item tricks of Quantum's bot, done the way a player does them: look at a block face and
 * right-click with the item from the hotbar.
 *
 * <ul>
 *   <li>Cobweb: after landing a hit, when the opponent flies up out of reach, a cobweb goes where
 *       they will land.</li>
 *   <li>Water: stuck in a cobweb or burning, the bot pours water at its feet and scoops it back up.</li>
 *   <li>Lava: an opponent knocked into the air gets lava under them; it is picked up again later.</li>
 * </ul>
 * Each trick has a cooldown and, like in Quantum's datapack, only fires on half of its chances.
 */
final class Utilities {
	private static final int COBWEB_COOLDOWN = 15;
	private static final int LAVA_COOLDOWN = 10;
	private static final int WATER_COOLDOWN = 10;
	/** Ticks an action may take to line up the crosshair before it is dropped. */
	private static final int ACTION_TIMEOUT = 6;
	/** Placed lava is scooped up again after this many ticks. */
	private static final int LAVA_PICKUP_AGE = 30;
	private static final int MAX_SUPPORT_DEPTH = 4;

	private enum Kind {
		COBWEB,
		LAVA,
		WATER,
		PICKUP_WATER,
		PICKUP_LAVA
	}

	private static final class Action {
		final Kind kind;
		final int slot;
		final Vec3 aimPoint;
		final @Nullable BlockPos support;
		final @Nullable BlockPos fluidPos;
		int ticksLeft = ACTION_TIMEOUT;
		boolean aimed;

		Action(Kind kind, int slot, Vec3 aimPoint, @Nullable BlockPos support, @Nullable BlockPos fluidPos) {
			this.kind = kind;
			this.slot = slot;
			this.aimPoint = aimPoint;
			this.support = support;
			this.fluidPos = fluidPos;
		}
	}

	private static final class PlacedFluid {
		final BlockPos pos;
		final boolean lava;
		int age;

		PlacedFluid(BlockPos pos, boolean lava) {
			this.pos = pos;
			this.lava = lava;
		}
	}

	private final RandomSource random;
	private final List<PlacedFluid> placed = new ArrayList<>();
	private @Nullable Action action;
	private int cobwebCd;
	private int lavaCd;
	private int waterCd;

	Utilities(RandomSource random) {
		this.random = random;
	}

	boolean isBusy() {
		return this.action != null;
	}

	void reset() {
		this.action = null;
	}

	/**
	 * Runs one tick. Returns true while an item trick is in progress; the caller then leaves the
	 * hotbar, camera and right click alone and does not swing.
	 */
	boolean tick(Minecraft minecraft, LocalPlayer player, LivingEntity enemy, int realHitCd, boolean enemyInReach, ClientConfig config) {
		this.cobwebCd = Math.max(0, this.cobwebCd - 1);
		this.lavaCd = Math.max(0, this.lavaCd - 1);
		this.waterCd = Math.max(0, this.waterCd - 1);
		this.forgetGoneFluids(player.level());

		if (this.action == null) {
			this.action = this.decide(player, enemy, realHitCd, enemyInReach, config);
			if (this.action == null) {
				return false;
			}
		}
		return this.continueAction(minecraft, player);
	}

	// ------------------------------------------------------------------------------------------
	// Deciding

	private @Nullable Action decide(LocalPlayer player, LivingEntity enemy, int realHitCd, boolean enemyInReach, ClientConfig config) {
		if (player.isUsingItem()) {
			return null;
		}
		Level level = player.level();

		// Stuck in a cobweb or burning: water at our feet.
		boolean stuck = isCobweb(level, player.blockPosition()) || isCobweb(level, BlockPos.containing(player.getEyePosition()));
		boolean burning = player.isOnFire() && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isInWater();
		if (config.useWater && this.waterCd == 0 && (stuck || burning && !enemyInReach)) {
			int slot = Hotbar.find(player, Items.WATER_BUCKET);
			if (slot >= 0) {
				this.waterCd = WATER_COOLDOWN;
				if (this.random.nextBoolean()) {
					Vec3 feet = player.position();
					return new Action(Kind.WATER, slot, new Vec3(feet.x, feet.y - 0.5, feet.z), null, player.blockPosition());
				}
			}
		}

		// Scoop our water back up once we are free.
		if (!stuck) {
			Action pickup = this.pickup(player, false, 0);
			if (pickup != null) {
				return pickup;
			}
		}

		// Knocked the opponent up and out of reach after our hit: cobweb or lava where they land.
		boolean opening = realHitCd >= 1 && enemy.getDeltaMovement().y >= 0.0 && !enemyInReach;
		boolean enemyStuck = isCobweb(level, enemy.blockPosition());
		if (config.useCobwebs && this.cobwebCd == 0 && opening && !enemyStuck && player.onGround()) {
			int slot = Hotbar.find(player, Items.COBWEB);
			if (slot >= 0) {
				this.cobwebCd = COBWEB_COOLDOWN;
				Action web = this.random.nextBoolean() ? this.placeUnder(player, enemy, Kind.COBWEB, slot) : null;
				if (web != null) {
					return web;
				}
			}
		}

		boolean enemyCanBurn = !enemy.onGround() && !enemy.isOnFire() && !enemy.hasEffect(MobEffects.FIRE_RESISTANCE);
		if (config.useLava && this.lavaCd == 0 && opening && enemyCanBurn) {
			int slot = Hotbar.find(player, Items.LAVA_BUCKET);
			if (slot >= 0) {
				this.lavaCd = LAVA_COOLDOWN;
				Action lava = this.random.nextBoolean() ? this.placeUnder(player, enemy, Kind.LAVA, slot) : null;
				if (lava != null) {
					return lava;
				}
			}
		}

		return this.pickup(player, true, LAVA_PICKUP_AGE);
	}

	/** Places a cobweb or lava on the ground below the opponent, where they will land. */
	private @Nullable Action placeUnder(LocalPlayer player, LivingEntity enemy, Kind kind, int slot) {
		Level level = player.level();
		BlockPos support = null;
		BlockPos cell = BlockPos.containing(enemy.getX(), enemy.getY() + 0.01, enemy.getZ());
		for (int depth = 0; depth < MAX_SUPPORT_DEPTH; depth++) {
			BlockPos below = cell.below();
			if (isSolid(level, below)) {
				support = below;
				break;
			}
			cell = below;
		}
		if (support == null) {
			return null;
		}

		BlockPos target = support.above();
		if (!level.getBlockState(target).canBeReplaced() || !level.getFluidState(target).isEmpty()) {
			return null;
		}
		// Never pour lava next to ourselves.
		if (kind == Kind.LAVA && Vec3.atBottomCenterOf(target).distanceToSqr(player.position()) < 2.5 * 2.5) {
			return null;
		}

		Vec3 point = this.findTopFacePoint(player, support, enemy);
		return point == null ? null : new Action(kind, slot, point, support, kind == Kind.LAVA ? target : null);
	}

	/** Picks up water (right after use) or lava (after it had time to burn) we poured ourselves. */
	private @Nullable Action pickup(LocalPlayer player, boolean lava, int minAge) {
		int bucket = Hotbar.find(player, Items.BUCKET);
		if (bucket < 0) {
			return null;
		}
		double reach = player.blockInteractionRange() - 0.3;
		for (PlacedFluid fluid : this.placed) {
			if (fluid.lava != lava || fluid.age < minAge) {
				continue;
			}
			Vec3 center = Vec3.atCenterOf(fluid.pos);
			if (center.distanceTo(player.getEyePosition()) <= reach) {
				return new Action(lava ? Kind.PICKUP_LAVA : Kind.PICKUP_WATER, bucket, center, null, fluid.pos);
			}
		}
		return null;
	}

	/**
	 * A point on the top face of {@code support} that our eyes can see and click without the
	 * opponent's hitbox or another block in the way.
	 */
	private @Nullable Vec3 findTopFacePoint(LocalPlayer player, BlockPos support, LivingEntity enemy) {
		Vec3 eye = player.getEyePosition();
		double reach = player.blockInteractionRange() - 0.2;
		Vec3 best = null;
		double bestDistance = Double.MAX_VALUE;
		for (double dx = 0.15; dx <= 0.851; dx += 0.175) {
			for (double dz = 0.15; dz <= 0.851; dz += 0.175) {
				Vec3 point = new Vec3(support.getX() + dx, support.getY() + 1.0, support.getZ() + dz);
				double distance = eye.distanceTo(point);
				if (distance > reach || distance >= bestDistance) {
					continue;
				}
				if (enemy.getBoundingBox().inflate(0.05).clip(eye, point).isPresent()) {
					continue;
				}
				BlockHitResult hit = player.level().clip(new ClipContext(eye, point.add(point.subtract(eye).normalize().scale(0.05)),
					ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
				if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(support) && hit.getDirection() == Direction.UP) {
					best = point;
					bestDistance = distance;
				}
			}
		}
		return best;
	}

	// ------------------------------------------------------------------------------------------
	// Doing

	private boolean continueAction(Minecraft minecraft, LocalPlayer player) {
		Action current = this.action;
		if (current == null) {
			return false;
		}
		if (--current.ticksLeft < 0 || player.getInventory().getItem(current.slot).isEmpty()) {
			this.action = null;
			return false;
		}

		player.getInventory().setSelectedSlot(current.slot);
		lookAt(player, current.aimPoint);
		if (!current.aimed) {
			// The crosshair is worked out at the start of the next tick.
			current.aimed = true;
			return true;
		}

		if (!this.crosshairReady(minecraft, current)) {
			return true;
		}

		BotController.click(minecraft.options.keyUse);
		switch (current.kind) {
			case LAVA -> this.placed.add(new PlacedFluid(current.fluidPos, true));
			case WATER -> this.rememberWaterNear(player);
			case PICKUP_WATER, PICKUP_LAVA -> this.placed.removeIf(fluid -> fluid.pos.equals(current.fluidPos));
			default -> {
			}
		}
		this.action = null;
		return true;
	}

	private boolean crosshairReady(Minecraft minecraft, Action current) {
		HitResult hit = minecraft.hitResult;
		return switch (current.kind) {
			case COBWEB, LAVA -> hit instanceof BlockHitResult block
				&& hit.getType() == HitResult.Type.BLOCK
				&& block.getBlockPos().equals(current.support)
				&& block.getDirection() == Direction.UP;
			case WATER -> hit != null && hit.getType() == HitResult.Type.BLOCK;
			// The bucket looks for the source block itself.
			case PICKUP_WATER, PICKUP_LAVA -> true;
		};
	}

	/**
	 * Where the water lands depends on what the bucket hits first: the ground, a cobweb at our feet,
	 * or (with the head in a cobweb) the block above our head. Remember all of them; the ones without
	 * a water source are forgotten a few ticks later.
	 */
	private void rememberWaterNear(LocalPlayer player) {
		BlockPos feet = player.blockPosition();
		for (int dy = -1; dy <= 2; dy++) {
			this.placed.add(new PlacedFluid(feet.above(dy), false));
		}
	}

	private void forgetGoneFluids(Level level) {
		Iterator<PlacedFluid> iterator = this.placed.iterator();
		while (iterator.hasNext()) {
			PlacedFluid fluid = iterator.next();
			fluid.age++;
			FluidState state = level.getFluidState(fluid.pos);
			boolean present = state.isSource() && state.is(fluid.lava ? FluidTags.LAVA : FluidTags.WATER);
			// Give freshly poured water a moment to show up before forgetting it.
			if (!present && (fluid.lava || fluid.age > 3) || fluid.age > 600) {
				iterator.remove();
			}
		}
	}

	private static void lookAt(LocalPlayer player, Vec3 point) {
		Vec3 eye = player.getEyePosition();
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		player.setYRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
		player.setXRot(Mth.clamp((float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG)), -90.0F, 90.0F));
	}

	private static boolean isCobweb(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(Blocks.COBWEB);
	}

	private static boolean isSolid(Level level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}
}
