package dev.qwantium.pvpbot.client.bot;

import dev.qwantium.pvpbot.client.BotPermission;
import dev.qwantium.pvpbot.client.ClientConfig;
import dev.qwantium.pvpbot.mixin.client.KeyMappingAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The PvP brain. Drives your own player through the same inputs you would use: it turns the camera,
 * presses movement keys, clicks to attack when the crosshair is on the opponent and holds right click
 * to block or eat. Nothing is sent to the server that a player could not do by hand.
 *
 * <p>{@link #tick} runs at the start of every client tick (before vanilla handles keys and moves the
 * player); {@link #onFrame} runs every frame so the camera turns smoothly.
 */
public final class BotController {
	private static final BotController INSTANCE = new BotController();

	/** Health (half hearts) at which a totem is moved into the offhand. */
	private static final float TOTEM_HEALTH = 8.0F;
	/** Health at which the bot eats even while the opponent is right in front of it. */
	private static final float PANIC_EAT_HEALTH = 6.0F;
	private static final int HUNGRY_FOOD_LEVEL = 6;
	private static final int RESPAWN_DELAY_TICKS = 30;
	private static final int OFFHAND_SWAP_COOLDOWN = 10;
	private static final int MAX_EAT_TICKS = 60;
	private static final int MAX_CRIT_WAIT_TICKS = 12;

	private final RandomSource random = RandomSource.create();
	private ClientConfig config = new ClientConfig();

	private boolean active;
	private boolean engaged;
	private @Nullable LivingEntity target;
	private int engagedTicks;
	private Input intent = Input.EMPTY;

	// aim
	private @Nullable Vec3 aimOffset;
	private Vec3 jitter = Vec3.ZERO;
	private Vec3 jitterGoal = Vec3.ZERO;
	private int jitterTicks;
	private long lastFrameNanos;

	// fighting
	private double nextCharge = 1.0;
	private boolean strafing;
	private int strafeDir = 1;
	private int strafeTicks;
	private int wTapTicks;
	private boolean critPlanned;
	private int critWaitTicks;
	private int blockTicks;
	private int stuckTicks;

	// items
	private boolean eating;
	private int eatSlot = -1;
	private int eatTicks;
	private int offhandCooldown;
	private boolean holdingUse;
	private int deadTicks;

	private BotController() {
	}

	public static BotController get() {
		return INSTANCE;
	}

	public void setConfig(ClientConfig config) {
		this.config = config;
	}

	public ClientConfig config() {
		return this.config;
	}

	public boolean isActive() {
		return this.active;
	}

	/** True while the bot is fighting someone and has taken over movement and camera. */
	public boolean isEngaged() {
		return this.active && this.engaged;
	}

	public @Nullable LivingEntity target() {
		return this.engaged ? this.target : null;
	}

	Input intent() {
		return this.intent;
	}

	public void toggle(Minecraft minecraft) {
		this.setActive(minecraft, !this.active);
	}

	public void setActive(Minecraft minecraft, boolean active) {
		LocalPlayer player = minecraft.player;
		if (active == this.active || player == null) {
			return;
		}

		if (active && !BotPermission.isAllowed(minecraft)) {
			player.displayClientMessage(Component.translatable("qwantiumbot.msg.not_allowed").withStyle(ChatFormatting.RED), false);
			return;
		}

		this.active = active;
		if (active) {
			this.installInput(player);
			player.displayClientMessage(
				Component.translatable(
					"qwantiumbot.msg.enabled",
					Component.translatable(this.config.difficulty.translationKey()),
					describeTarget(this.config)
				).withStyle(ChatFormatting.GREEN),
				true
			);
		} else {
			this.shutdown(minecraft);
			player.displayClientMessage(Component.translatable("qwantiumbot.msg.disabled").withStyle(ChatFormatting.YELLOW), true);
		}
	}

	/** Turns the bot off without chat feedback, e.g. when leaving a world. */
	public void forceOff(Minecraft minecraft) {
		if (this.active) {
			this.active = false;
			this.shutdown(minecraft);
		}
	}

	public static Component describeTarget(ClientConfig config) {
		return switch (config.targetMode) {
			case NEAREST_PLAYER -> Component.translatable("qwantiumbot.target.nearest_player");
			case PLAYER -> Component.translatable("qwantiumbot.target.player", config.targetName);
			case NEAREST_ANY -> Component.translatable("qwantiumbot.target.nearest_any");
		};
	}

	// ------------------------------------------------------------------------------------------
	// Tick

	public void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (!this.active || player == null || minecraft.level == null || minecraft.gameMode == null) {
			return;
		}

		if (!BotPermission.isAllowed(minecraft)) {
			this.setActive(minecraft, false);
			player.displayClientMessage(Component.translatable("qwantiumbot.msg.not_allowed").withStyle(ChatFormatting.RED), false);
			return;
		}

		this.installInput(player);

		if (player.isDeadOrDying()) {
			this.disengage(minecraft);
			if (this.config.autoRespawn && minecraft.screen instanceof DeathScreen && ++this.deadTicks >= RESPAWN_DELAY_TICKS) {
				this.deadTicks = 0;
				player.respawn();
				minecraft.setScreen(null);
			}
			return;
		}
		this.deadTicks = 0;

		if (minecraft.screen != null || player.isSpectator()) {
			this.disengage(minecraft);
			return;
		}

		LivingEntity next = TargetFinder.find(player, minecraft.level, this.config, this.target);
		if (next == null) {
			this.disengage(minecraft);
			return;
		}

		if (!this.engaged || next != this.target) {
			this.engage(next);
		}
		this.engagedTicks++;
		this.fight(minecraft, player, next);
	}

	private void engage(LivingEntity newTarget) {
		this.engaged = true;
		this.target = newTarget;
		this.engagedTicks = 0;
		this.aimOffset = null;
		this.critPlanned = false;
		this.blockTicks = 0;
		this.nextCharge = this.rollCharge();
	}

	private void disengage(Minecraft minecraft) {
		if (!this.engaged) {
			return;
		}

		this.engaged = false;
		this.target = null;
		this.intent = Input.EMPTY;
		this.aimOffset = null;
		this.lastFrameNanos = 0L;
		this.eating = false;
		this.blockTicks = 0;
		this.wTapTicks = 0;
		this.setUse(minecraft, false);
	}

	private void shutdown(Minecraft minecraft) {
		this.disengage(minecraft);
		LocalPlayer player = minecraft.player;
		if (player != null && player.input instanceof BotInput botInput) {
			player.input = botInput.keyboard();
		}
	}

	private void installInput(LocalPlayer player) {
		// A new LocalPlayer (respawn, dimension change) comes with a fresh keyboard input.
		if (!(player.input instanceof BotInput)) {
			player.input = new BotInput(player.input);
		}
	}

	private void fight(Minecraft minecraft, LocalPlayer player, LivingEntity enemy) {
		Difficulty difficulty = this.config.difficulty;
		if (this.offhandCooldown > 0) {
			this.offhandCooldown--;
		}

		this.updateAim(player, enemy, difficulty);

		double distance = player.distanceTo(enemy);
		double reach = player.entityInteractionRange();
		boolean inReach = enemy.getBoundingBox().distanceToSqr(player.getEyePosition()) <= reach * reach;

		if (this.eating) {
			this.continueEating(minecraft, player, enemy);
			return;
		}

		if (this.shouldEat(player, distance, difficulty)) {
			this.startEating(minecraft, player);
			this.continueEating(minecraft, player, enemy);
			return;
		}

		if (!player.isUsingItem() && this.offhandCooldown == 0 && this.manageOffhand(minecraft, player)) {
			this.offhandCooldown = OFFHAND_SWAP_COOLDOWN;
			this.move(player, enemy, distance, reach, false, difficulty);
			return;
		}

		boolean breakShield = this.chooseWeapon(player, enemy, difficulty);
		boolean block = this.decideBlock(player, enemy, distance, difficulty);
		this.setUse(minecraft, block);
		this.move(player, enemy, distance, reach, block, difficulty);
		if (!block) {
			this.tryAttack(minecraft, player, enemy, inReach, breakShield, difficulty);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Attacking

	private void tryAttack(Minecraft minecraft, LocalPlayer player, LivingEntity enemy, boolean inReach, boolean breakShield, Difficulty difficulty) {
		if (!inReach || player.isUsingItem() || this.engagedTicks < difficulty.reactionTicks) {
			return;
		}
		// Only swing when the crosshair is really on the opponent, exactly like a player clicking.
		if (!(minecraft.hitResult instanceof EntityHitResult hit) || hit.getEntity() != enemy) {
			return;
		}

		// An axe disables a raised shield no matter how charged the swing is.
		if (!breakShield) {
			if (player.getAttackStrengthScale(0.5F) < this.nextCharge) {
				return;
			}

			if (this.critPlanned) {
				boolean falling = !player.onGround() && player.getDeltaMovement().y < -0.08 && !player.isSprinting();
				if (!falling && this.critWaitTicks++ < MAX_CRIT_WAIT_TICKS) {
					return;
				}
			}
		}

		click(minecraft.options.keyAttack);
		this.afterAttack(player, difficulty);
	}

	private void afterAttack(LocalPlayer player, Difficulty difficulty) {
		this.nextCharge = this.rollCharge();
		this.critWaitTicks = 0;
		this.critPlanned = this.random.nextDouble() < difficulty.critChance;
		if (player.isSprinting() && this.random.nextDouble() < difficulty.wTapChance) {
			this.wTapTicks = 2;
		}

		if (this.canBlock(player) && this.random.nextDouble() < difficulty.shieldChance) {
			// Raise the shield between hits and drop it a moment before the next swing is charged.
			int ticksToCharge = Mth.ceil(player.getCurrentItemAttackStrengthDelay() * this.nextCharge);
			this.blockTicks = Math.max(0, ticksToCharge - 3);
		}
	}

	private double rollCharge() {
		Difficulty difficulty = this.config.difficulty;
		return Mth.lerp(this.random.nextDouble(), difficulty.minCharge, difficulty.maxCharge);
	}

	/** Selects the weapon to fight with. Returns true when it switched to an axe to break a shield. */
	private boolean chooseWeapon(LocalPlayer player, LivingEntity enemy, Difficulty difficulty) {
		if (player.isUsingItem()) {
			return false;
		}

		int slot = Hotbar.bestWeapon(player);
		boolean breakShield = false;
		if (difficulty != Difficulty.EASY && isShieldUp(enemy)) {
			int axe = Hotbar.findAxe(player);
			if (axe >= 0) {
				slot = axe;
				breakShield = true;
			}
		}

		if (slot >= 0 && slot != player.getInventory().getSelectedSlot()) {
			player.getInventory().setSelectedSlot(slot);
		}
		return breakShield;
	}

	// ------------------------------------------------------------------------------------------
	// Shield, totem, food

	private boolean canBlock(LocalPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		ItemStack mainHand = player.getMainHandItem();
		return offhand.has(DataComponents.BLOCKS_ATTACKS)
			&& !player.getCooldowns().isOnCooldown(offhand)
			&& (mainHand.isEmpty() || Hotbar.isMeleeWeapon(mainHand));
	}

	private boolean decideBlock(LocalPlayer player, LivingEntity enemy, double distance, Difficulty difficulty) {
		if (!this.canBlock(player)) {
			this.blockTicks = 0;
			return false;
		}

		// Arrows and tridents: keep the shield up while the opponent aims from range.
		if (distance > 5.0 && difficulty.shieldChance > 0.0 && isAimingProjectile(enemy)) {
			return true;
		}

		// An axe would just disable the shield, so a good player does not block against one.
		if (difficulty != Difficulty.EASY && enemy.getMainHandItem().is(ItemTags.AXES)) {
			this.blockTicks = 0;
			return false;
		}

		if (this.blockTicks > 0 && distance < 5.0) {
			this.blockTicks--;
			return true;
		}
		this.blockTicks = 0;
		return false;
	}

	/** Moves a totem (when low) or a shield (when empty) into the offhand with the swap-hands key. */
	private boolean manageOffhand(Minecraft minecraft, LocalPlayer player) {
		ItemStack offhand = player.getOffhandItem();
		int slot = -1;
		if (player.getHealth() <= TOTEM_HEALTH && !offhand.has(DataComponents.DEATH_PROTECTION)) {
			slot = Hotbar.findTotem(player);
		}
		if (slot < 0 && offhand.isEmpty()) {
			slot = Hotbar.findShield(player);
			if (slot < 0) {
				slot = Hotbar.findTotem(player);
			}
		}
		if (slot < 0) {
			return false;
		}

		player.getInventory().setSelectedSlot(slot);
		click(minecraft.options.keySwapOffhand);
		return true;
	}

	private boolean shouldEat(LocalPlayer player, double distance, Difficulty difficulty) {
		if (player.getHealth() <= difficulty.eatHealth && Hotbar.findGoldenApple(player) >= 0) {
			return distance > 3.2 || player.getHealth() <= PANIC_EAT_HEALTH;
		}
		return player.getFoodData().getFoodLevel() <= HUNGRY_FOOD_LEVEL && distance > 6.0 && Hotbar.findFood(player) >= 0;
	}

	private void startEating(Minecraft minecraft, LocalPlayer player) {
		int slot = Hotbar.findGoldenApple(player);
		if (slot < 0) {
			slot = Hotbar.findFood(player);
		}
		if (slot < 0) {
			return;
		}

		player.getInventory().setSelectedSlot(slot);
		this.eating = true;
		this.eatSlot = slot;
		this.eatTicks = 0;
		this.blockTicks = 0;
		this.setUse(minecraft, true);
	}

	private void continueEating(Minecraft minecraft, LocalPlayer player, LivingEntity enemy) {
		this.eatTicks++;
		ItemStack held = player.getMainHandItem();
		boolean stillFood = held.has(DataComponents.FOOD) && player.getInventory().getSelectedSlot() == this.eatSlot;
		boolean finished = this.eatTicks > 4 && !player.isUsingItem();
		if (!this.eating || !stillFood || finished || this.eatTicks > MAX_EAT_TICKS) {
			// Release right click; the next tick switches back to the weapon.
			this.eating = false;
			this.setUse(minecraft, false);
			this.retreat(player, enemy);
			return;
		}

		this.setUse(minecraft, true);
		this.retreat(player, enemy);
	}

	// ------------------------------------------------------------------------------------------
	// Movement

	private void move(LocalPlayer player, LivingEntity enemy, double distance, double reach, boolean blocking, Difficulty difficulty) {
		this.updateStrafe(difficulty);

		boolean forward;
		boolean backward = false;
		int strafe = 0;
		boolean sprint;
		boolean jump = false;

		if (distance > reach + 0.6) {
			// Chase. A little zig-zag on harder levels makes the bot harder to hit on approach.
			forward = true;
			sprint = true;
			if (this.strafing && difficulty.strafeChance >= 0.6 && distance < 10.0) {
				strafe = this.strafeDir;
			}
		} else {
			// In range: keep the pressure on and circle the opponent.
			forward = distance > 1.6;
			sprint = forward;
			if (this.strafing) {
				strafe = this.strafeDir;
			}
			if (distance < 1.0) {
				forward = false;
				backward = true;
			}
		}

		// W-tap: let go of forward for a moment after a hit so the next hit gets sprint knockback again.
		if (this.wTapTicks > 0) {
			this.wTapTicks--;
			forward = false;
			sprint = false;
		}

		// Jump critical: hop when the swing is almost charged, stop sprinting in the air.
		if (this.critPlanned && !blocking) {
			float charge = player.getAttackStrengthScale(0.5F);
			if (player.onGround() && distance < reach + 0.4 && charge >= this.nextCharge - 0.25) {
				jump = true;
			} else if (!player.onGround()) {
				forward = false;
				sprint = false;
			}
		}

		if (blocking) {
			sprint = false;
		}

		this.stuckTicks = forward && player.horizontalCollision ? this.stuckTicks + 1 : 0;
		if (player.onGround() && player.horizontalCollision && (forward || strafe != 0)) {
			jump = true;
		}
		if (this.stuckTicks > 20) {
			strafe = this.strafeDir;
			if (this.stuckTicks % 20 == 0) {
				this.strafeDir = -this.strafeDir;
			}
		}
		if (player.onGround() && enemy.getY() - player.getY() > 0.6 && horizontalDistance(player, enemy) < 2.5) {
			jump = true;
		}
		if (player.isInWater() || player.isInLava()) {
			jump = true;
		}

		this.setIntent(player, forward, backward, strafe, jump, sprint);
	}

	/** Backs away from the opponent while eating, still facing them. */
	private void retreat(LocalPlayer player, LivingEntity enemy) {
		this.updateStrafe(this.config.difficulty);
		boolean jump = player.onGround() && player.horizontalCollision;
		if (player.isInWater() || player.isInLava()) {
			jump = true;
		}
		this.setIntent(player, false, true, this.strafing ? this.strafeDir : 0, jump, false);
	}

	private void setIntent(LocalPlayer player, boolean forward, boolean backward, int strafe, boolean jump, boolean sprint) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double leftX = Mth.cos(yaw);
		double leftZ = Mth.sin(yaw);

		if (forward && Terrain.isUnsafe(player.level(), player, forwardX, forwardZ)) {
			forward = false;
			sprint = false;
		}
		if (backward && Terrain.isUnsafe(player.level(), player, -forwardX, -forwardZ)) {
			backward = false;
		}
		if (strafe != 0 && Terrain.isUnsafe(player.level(), player, leftX * strafe, leftZ * strafe)) {
			strafe = -strafe;
			this.strafeDir = strafe;
			if (Terrain.isUnsafe(player.level(), player, leftX * strafe, leftZ * strafe)) {
				strafe = 0;
			}
		}

		this.intent = BotInput.keys(forward, backward, strafe > 0, strafe < 0, jump, sprint && forward);
	}

	private void updateStrafe(Difficulty difficulty) {
		if (--this.strafeTicks <= 0) {
			this.strafing = this.random.nextDouble() < difficulty.strafeChance;
			this.strafeDir = this.random.nextBoolean() ? 1 : -1;
			this.strafeTicks = 8 + this.random.nextInt(18);
		}
	}

	private static double horizontalDistance(LivingEntity a, LivingEntity b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ------------------------------------------------------------------------------------------
	// Aiming

	private void updateAim(LocalPlayer player, LivingEntity enemy, Difficulty difficulty) {
		if (--this.jitterTicks <= 0) {
			double amount = difficulty.aimJitter;
			this.jitterGoal = new Vec3(
				(this.random.nextDouble() * 2.0 - 1.0) * amount,
				(this.random.nextDouble() * 2.0 - 1.0) * amount * 0.6,
				(this.random.nextDouble() * 2.0 - 1.0) * amount
			);
			this.jitterTicks = 4 + this.random.nextInt(6);
		}
		this.jitter = this.jitter.lerp(this.jitterGoal, 0.25);

		// Aim at the point of the hitbox closest to our eyes (pulled inwards a bit), which is
		// what lands hits at the edge of reach.
		Vec3 eye = player.getEyePosition();
		AABB box = enemy.getBoundingBox();
		double insetX = box.getXsize() * 0.3;
		double insetY = box.getYsize() * 0.2;
		double insetZ = box.getZsize() * 0.3;
		Vec3 point = new Vec3(
			Mth.clamp(eye.x, box.minX + insetX, box.maxX - insetX),
			Mth.clamp(eye.y, box.minY + insetY, box.maxY - insetY),
			Mth.clamp(eye.z, box.minZ + insetZ, box.maxZ - insetZ)
		);

		Vec3 velocity = enemy.position().subtract(enemy.xo, enemy.yo, enemy.zo);
		point = point.add(velocity.scale(difficulty.predictTicks)).add(this.jitter);
		this.aimOffset = point.subtract(enemy.position());
	}

	/** Called every frame: turns the camera towards the aim point with a speed limit. */
	public void onFrame(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		LivingEntity enemy = this.target;
		Vec3 offset = this.aimOffset;
		if (!this.isEngaged() || player == null || enemy == null || offset == null) {
			this.lastFrameNanos = 0L;
			return;
		}

		long now = System.nanoTime();
		if (this.lastFrameNanos == 0L) {
			this.lastFrameNanos = now;
			return;
		}
		double seconds = Math.min((now - this.lastFrameNanos) / 1.0E9, 0.1);
		this.lastFrameNanos = now;

		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 enemyPos = new Vec3(
			Mth.lerp(partialTick, enemy.xo, enemy.getX()),
			Mth.lerp(partialTick, enemy.yo, enemy.getY()),
			Mth.lerp(partialTick, enemy.zo, enemy.getZ())
		);
		Vec3 aim = enemyPos.add(offset);
		Vec3 eye = player.getEyePosition(partialTick);
		double dx = aim.x - eye.x;
		double dy = aim.y - eye.y;
		double dz = aim.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float wantedYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float wantedPitch = (float) (-(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG));

		Difficulty difficulty = this.config.difficulty;
		double follow = 1.0 - Math.exp(-difficulty.aimGain * seconds);
		float maxStep = (float) (difficulty.aimSpeed * seconds);
		float yawStep = Mth.clamp((float) (Mth.wrapDegrees(wantedYaw - player.getYRot()) * follow), -maxStep, maxStep);
		float pitchStep = Mth.clamp((float) ((wantedPitch - player.getXRot()) * follow), -maxStep, maxStep);

		player.setYRot(player.getYRot() + yawStep);
		player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -90.0F, 90.0F));
	}

	// ------------------------------------------------------------------------------------------
	// Opponent state

	private static boolean isShieldUp(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	private static boolean isAimingProjectile(LivingEntity entity) {
		ItemStack held = entity.isUsingItem() ? entity.getUseItem() : entity.getMainHandItem();
		ItemUseAnimation animation = held.getUseAnimation();
		if (entity.isUsingItem() && (animation == ItemUseAnimation.BOW || animation == ItemUseAnimation.SPEAR)) {
			return true;
		}
		return held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held);
	}

	// ------------------------------------------------------------------------------------------
	// Keys

	private void setUse(Minecraft minecraft, boolean down) {
		if (down || this.holdingUse) {
			// Write the key state directly so it also works with "toggle use" in the controls.
			((KeyMappingAccessor) minecraft.options.keyUse).qwantium$setDown(down);
		}
		this.holdingUse = down;
	}

	private static void click(KeyMapping key) {
		KeyMappingAccessor accessor = (KeyMappingAccessor) key;
		accessor.qwantium$setClickCount(accessor.qwantium$getClickCount() + 1);
	}
}
