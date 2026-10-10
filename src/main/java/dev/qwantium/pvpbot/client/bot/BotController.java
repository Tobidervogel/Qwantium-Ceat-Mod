package dev.qwantium.pvpbot.client.bot;

import dev.qwantium.pvpbot.client.BotPermission;
import dev.qwantium.pvpbot.client.ClientConfig;
import dev.qwantium.pvpbot.client.QuantumRemote;
import dev.qwantium.pvpbot.network.QuantumControlPayload;
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
 * <p>The fighting style follows the sword bot of Quantum's PvP Practice: after every hit it rolls a
 * new reach, aim lag and play style (sprint-hit combo or jump crit, strafing or not), backs off for a
 * few ticks after landing a hit (S-tap), keeps its distance when too close (W-tap), jump-resets when
 * hit and re-rolls its strafe direction every 5 ticks.
 *
 * <p>{@link #tick} runs at the start of every client tick (before vanilla handles keys and moves the
 * player); {@link #onFrame} runs every frame so the camera turns smoothly.
 */
public final class BotController {
	private static final BotController INSTANCE = new BotController();

	/** Ticks between swings: the next swing comes this many ticks + 1 after a hit. */
	private static final int HIT_COOLDOWN = 11;
	/** Crit style: jump once the hit cooldown is down to this (two ticks after a hit). */
	private static final int CRIT_JUMP_COOLDOWN = 9;
	/** S-tap: walk backwards while the time since our own hit is at least this. */
	private static final int S_TAP_FROM = 7;
	/** W-tap: stop walking forward when the opponent is this close (feet to feet). */
	private static final double TOO_CLOSE = 1.8;
	private static final int STRAFE_REROLL_TICKS = 5;
	/** A crit is skipped for a normal hit when the bot has waited this long in the air. */
	private static final int MAX_CRIT_WAIT_TICKS = 6;
	private static final double MAX_REACH = 3.0;
	/** Health (half hearts) at which a totem is moved into the offhand. */
	private static final float TOTEM_HEALTH = 8.0F;
	/** Health at which the bot eats even while the opponent is right in front of it. */
	private static final float PANIC_EAT_HEALTH = 6.0F;
	private static final int HUNGRY_FOOD_LEVEL = 6;
	private static final int RESPAWN_DELAY_TICKS = 30;
	private static final int OFFHAND_SWAP_COOLDOWN = 10;
	private static final int MAX_EAT_TICKS = 60;

	private final RandomSource random = RandomSource.create();
	private ClientConfig config = new ClientConfig();

	private boolean active;
	private boolean engaged;
	private @Nullable LivingEntity target;
	private Input intent = Input.EMPTY;

	// aim
	private @Nullable Vec3 aimOffset;
	private int aimLag = 1;
	private long lastFrameNanos;

	// fighting (names follow the scoreboards of Quantum's datapack)
	private int hitCd;
	private int realHitCd;
	private double tempReach = MAX_REACH;
	private boolean critStyle;
	private boolean strafeOn;
	private boolean shieldOn;
	private boolean strafeLeft;
	private int strafeCd;
	private int critWaitTicks;
	private int lastHurtTime;
	private boolean sprintingLastTick;
	private int stuckTicks;

	// items
	private boolean eating;
	private int eatSlot = -1;
	private int eatTicks;
	private int offhandCooldown;
	private boolean holdingUse;
	private int deadTicks;
	private final Utilities utilities = new Utilities(this.random);

	/** True while Quantum's bot plays this player through the Quantum bridge. */
	private boolean remoteEngaged;

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

	/** True while the bot (or Quantum's bot via the bridge) has taken over movement and camera. */
	public boolean isEngaged() {
		return this.active && this.engaged || this.remoteEngaged;
	}

	public boolean isQuantumControlled() {
		return this.remoteEngaged;
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
		if (this.remoteEngaged) {
			this.stopRemote(minecraft);
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
		if (player != null && QuantumRemote.isActive(minecraft)) {
			this.remoteTick(minecraft, player);
			return;
		}
		if (this.remoteEngaged) {
			this.stopRemote(minecraft);
		}

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
		this.fight(minecraft, player, next);
		this.lastHurtTime = player.hurtTime;
		this.sprintingLastTick = player.isSprinting();
	}

	private void engage(LivingEntity newTarget) {
		this.engaged = true;
		this.target = newTarget;
		this.aimOffset = null;
		this.hitCd = 0;
		this.realHitCd = 0;
		this.rollPlayStyle();
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
		this.utilities.reset();
		this.setUse(minecraft, false);
	}

	private void shutdown(Minecraft minecraft) {
		this.disengage(minecraft);
		LocalPlayer player = minecraft.player;
		if (!this.remoteEngaged && player != null && player.input instanceof BotInput botInput) {
			player.input = botInput.keyboard();
		}
	}

	// ------------------------------------------------------------------------------------------
	// Quantum bridge

	/** Plays the inputs Quantum's bot chose for us on the server as our own keys and camera. */
	private void remoteTick(Minecraft minecraft, LocalPlayer player) {
		if (this.engaged) {
			this.disengage(minecraft);
		}
		this.remoteEngaged = true;
		this.installInput(player);

		QuantumControlPayload inputs = QuantumRemote.latest();
		if (inputs == null || minecraft.screen != null || player.isDeadOrDying()) {
			this.intent = Input.EMPTY;
			return;
		}

		this.intent = new Input(
			inputs.forward() > 0.01F,
			inputs.forward() < -0.01F,
			inputs.strafe() > 0.01F,
			inputs.strafe() < -0.01F,
			QuantumRemote.consumeJump(),
			inputs.sneak(),
			inputs.sprint()
		);
		// Quantum drops the sprint for crits; the server must see it stop.
		if (!inputs.sprint() && player.isSprinting()) {
			player.setSprinting(false);
		}

		// Attacks happen on the server with the server's rotation; follow it so movement and view match.
		player.setYRot(inputs.yaw());
		player.setXRot(Mth.clamp(inputs.pitch(), -90.0F, 90.0F));

		if (inputs.slot() >= 0 && inputs.slot() < 9 && inputs.slot() != player.getInventory().getSelectedSlot()) {
			player.getInventory().setSelectedSlot(inputs.slot());
		}
	}

	private void stopRemote(Minecraft minecraft) {
		this.remoteEngaged = false;
		this.intent = Input.EMPTY;
		LocalPlayer player = minecraft.player;
		if (!this.active && player != null && player.input instanceof BotInput botInput) {
			player.input = botInput.keyboard();
		}
	}

	private void installInput(LocalPlayer player) {
		// A new LocalPlayer (respawn, dimension change) comes with a fresh keyboard input.
		if (!(player.input instanceof BotInput)) {
			player.input = new BotInput(player.input);
		}
	}

	/** Rolls reach, aim lag and play style for the next hit, like Quantum's bot does after every hit. */
	private void rollPlayStyle() {
		Difficulty difficulty = this.config.difficulty;
		int reach = difficulty.reachBase;
		for (int trial = 0; trial < 9 && reach < 30; trial++) {
			if (this.random.nextBoolean()) {
				reach++;
			}
		}
		this.tempReach = Math.min(reach / 10.0, MAX_REACH);

		int lag = difficulty.aimBase;
		for (int trial = 0; trial < 4 && lag > 1; trial++) {
			if (this.random.nextBoolean()) {
				lag--;
			}
		}
		this.aimLag = Math.max(1, lag);

		this.critStyle = this.random.nextBoolean();
		this.strafeOn = this.random.nextBoolean();
		this.shieldOn = this.random.nextBoolean();
	}

	private void fight(Minecraft minecraft, LocalPlayer player, LivingEntity enemy) {
		if (this.hitCd > -100) {
			this.hitCd--;
		}
		if (this.realHitCd > 0) {
			this.realHitCd--;
		}
		if (this.strafeCd > 0) {
			this.strafeCd--;
		}
		if (this.offhandCooldown > 0) {
			this.offhandCooldown--;
		}

		this.updateAim(player, enemy);

		double distance = player.distanceTo(enemy);
		double reachToBox = Math.sqrt(enemy.getBoundingBox().distanceToSqr(player.getEyePosition()));
		boolean inReach = reachToBox < this.tempReach && player.hasLineOfSight(enemy);
		// Keep backing off one tick longer while the opponent is in reach (Quantum's spacing).
		if (this.hitCd == 6 && inReach) {
			this.realHitCd++;
		}

		if (this.eating) {
			this.continueEating(minecraft, player, enemy);
			return;
		}

		if (this.shouldEat(player, distance)) {
			this.startEating(minecraft, player);
			this.continueEating(minecraft, player, enemy);
			return;
		}

		if (!player.isUsingItem() && this.offhandCooldown == 0 && this.manageOffhand(minecraft, player)) {
			this.offhandCooldown = OFFHAND_SWAP_COOLDOWN;
			this.move(player, enemy, distance, false);
			return;
		}

		// Quantum's item tricks (cobweb, water, lava) take the few ticks they need.
		if (this.utilities.tick(minecraft, player, enemy, this.realHitCd, inReach, this.config)) {
			this.setUse(minecraft, false);
			this.intent = Input.EMPTY;
			return;
		}

		boolean breakShield = this.chooseWeapon(player, enemy);
		boolean block = this.decideBlock(player, enemy, distance);
		this.setUse(minecraft, block);
		this.move(player, enemy, distance, block);
		if (!block) {
			this.tryAttack(minecraft, player, enemy, inReach, breakShield);
		}
	}

	// ------------------------------------------------------------------------------------------
	// Attacking

	private void tryAttack(Minecraft minecraft, LocalPlayer player, LivingEntity enemy, boolean inReach, boolean breakShield) {
		if (!inReach || player.isUsingItem()) {
			return;
		}
		// Only swing when the crosshair is really on the opponent, exactly like a player clicking.
		if (!(minecraft.hitResult instanceof EntityHitResult hit) || hit.getEntity() != enemy) {
			return;
		}

		// An axe disables a raised shield no matter how charged the swing is.
		if (!breakShield) {
			if (this.hitCd > 0 || player.getAttackStrengthScale(0.5F) < 0.9F) {
				return;
			}

			if (this.critStyle && !player.onGround()) {
				// The server must already see us not sprinting, so the swing comes a tick after
				// the sprint was dropped.
				boolean falling = player.getDeltaMovement().y < 0.0 && player.fallDistance > 0.1;
				if (!(falling && !this.sprintingLastTick && !player.isSprinting()) && this.critWaitTicks++ < MAX_CRIT_WAIT_TICKS) {
					return;
				}
			}
		}

		click(minecraft.options.keyAttack);
		this.hitCd = HIT_COOLDOWN;
		this.realHitCd = HIT_COOLDOWN;
		this.critWaitTicks = 0;
		this.rollPlayStyle();
	}

	/** Selects the weapon to fight with. Returns true when it switched to an axe to break a shield. */
	private boolean chooseWeapon(LocalPlayer player, LivingEntity enemy) {
		if (player.isUsingItem()) {
			return false;
		}

		int slot = Hotbar.bestWeapon(player);
		boolean breakShield = false;
		if (isShieldUp(enemy)) {
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

	private boolean decideBlock(LocalPlayer player, LivingEntity enemy, double distance) {
		if (!this.canBlock(player)) {
			return false;
		}

		// Arrows and tridents: keep the shield up while the opponent aims from range.
		if (distance > 5.0 && isAimingProjectile(enemy)) {
			return true;
		}

		// An axe would just disable the shield, so do not block against one.
		if (!this.shieldOn || enemy.getMainHandItem().is(ItemTags.AXES) || !player.onGround()) {
			return false;
		}

		// Shield up between hits, down two ticks before the next swing.
		return distance < 5.0 && this.realHitCd >= 3 && this.realHitCd <= 10;
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

	private boolean shouldEat(LocalPlayer player, double distance) {
		if (player.getHealth() <= this.config.difficulty.eatHealth && Hotbar.findGoldenApple(player) >= 0) {
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

	private void move(LocalPlayer player, LivingEntity enemy, double distance, boolean blocking) {
		boolean onGround = player.onGround();
		boolean forward = true;
		boolean sprint = true;
		boolean backward = false;
		boolean jump = false;

		// Jump reset: hop the moment we get hit to take less knockback.
		if (player.hurtTime > this.lastHurtTime && onGround) {
			jump = true;
		}

		if (this.critStyle) {
			// Jump two ticks after a hit so the bot is falling when the next swing is ready.
			if (onGround && this.hitCd <= CRIT_JUMP_COOLDOWN) {
				jump = true;
			}
			// Drop the sprint while falling into the hit: a sprinting hit cannot crit.
			if (!onGround && player.getDeltaMovement().y < 0.0 && this.hitCd <= 1) {
				sprint = false;
				player.setSprinting(false);
			}
		} else {
			// W-tap: do not walk into the opponent. S-tap: back off right after landing a hit.
			if (distance <= TOO_CLOSE) {
				forward = false;
			}
			if (this.realHitCd >= S_TAP_FROM) {
				forward = false;
				backward = true;
			}
		}

		int strafe = 0;
		if (onGround && this.strafeCd <= 0) {
			this.strafeCd = STRAFE_REROLL_TICKS;
			this.strafeLeft = this.random.nextBoolean();
		}
		if (this.strafeOn && onGround) {
			strafe = this.strafeLeft ? 1 : -1;
		}

		if (blocking) {
			sprint = false;
		}

		// Step up blocks, get unstuck, follow the opponent up and swim.
		this.stuckTicks = forward && player.horizontalCollision ? this.stuckTicks + 1 : 0;
		if (onGround && player.horizontalCollision && (forward || strafe != 0)) {
			jump = true;
		}
		if (this.stuckTicks > 20) {
			strafe = this.strafeLeft ? 1 : -1;
		}
		if (onGround && enemy.getY() - player.getY() > 0.6 && horizontalDistance(player, enemy) < 2.5) {
			jump = true;
		}
		if (player.isInWater() || player.isInLava()) {
			jump = true;
		}

		this.setIntent(player, forward, backward, strafe, jump, sprint);
	}

	/** Backs away from the opponent while eating, still facing them. */
	private void retreat(LocalPlayer player, LivingEntity enemy) {
		if (player.onGround() && this.strafeCd <= 0) {
			this.strafeCd = STRAFE_REROLL_TICKS;
			this.strafeLeft = this.random.nextBoolean();
		}
		boolean jump = player.onGround() && player.horizontalCollision;
		if (player.isInWater() || player.isInLava()) {
			jump = true;
		}
		this.setIntent(player, false, true, this.strafeLeft ? 1 : -1, jump, false);
	}

	private void setIntent(LocalPlayer player, boolean forward, boolean backward, int strafe, boolean jump, boolean sprint) {
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double leftX = Mth.cos(yaw);
		double leftZ = Mth.sin(yaw);

		boolean nearDanger = false;
		if (forward && Terrain.isUnsafe(player.level(), player, forwardX, forwardZ)) {
			forward = false;
			sprint = false;
			nearDanger = true;
		}
		if (backward && Terrain.isUnsafe(player.level(), player, -forwardX, -forwardZ)) {
			backward = false;
			nearDanger = true;
		}
		if (strafe != 0 && Terrain.isUnsafe(player.level(), player, leftX * strafe, leftZ * strafe)) {
			nearDanger = true;
			strafe = -strafe;
			this.strafeLeft = strafe > 0;
			if (Terrain.isUnsafe(player.level(), player, leftX * strafe, leftZ * strafe)) {
				strafe = 0;
			}
		}

		// Momentum counts too: a jump on the spot still carries the player along.
		Vec3 velocity = player.getDeltaMovement();
		double speed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		boolean driftingIntoDanger = speed > 0.05
			&& Terrain.isUnsafeJump(player.level(), player, velocity.x / speed, velocity.z / speed);

		if (jump && !player.isInWater() && !player.isInLava()) {
			double moveX = (forward ? forwardX : 0.0) - (backward ? forwardX : 0.0) + leftX * strafe;
			double moveZ = (forward ? forwardZ : 0.0) - (backward ? forwardZ : 0.0) + leftZ * strafe;
			double length = Math.sqrt(moveX * moveX + moveZ * moveZ);
			if (nearDanger || driftingIntoDanger
				|| length > 1.0E-4 && Terrain.isUnsafeJump(player.level(), player, moveX / length, moveZ / length)) {
				jump = false;
			}
		}

		// In the air and heading for danger: steer against the momentum.
		if (!player.onGround() && driftingIntoDanger) {
			double alongForward = velocity.x * forwardX + velocity.z * forwardZ;
			double alongLeft = velocity.x * leftX + velocity.z * leftZ;
			forward = alongForward < 0.0;
			backward = alongForward > 0.0;
			strafe = alongLeft > 0.0 ? -1 : 1;
			sprint = false;
		}

		this.intent = BotInput.keys(forward, backward, strafe > 0, strafe < 0, jump, sprint && forward);
	}

	private static double horizontalDistance(LivingEntity a, LivingEntity b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ------------------------------------------------------------------------------------------
	// Aiming

	/** Aims at the point of the opponent's hitbox closest to our eyes, pulled inwards a little. */
	private void updateAim(LocalPlayer player, LivingEntity enemy) {
		Vec3 eye = player.getEyePosition();
		AABB box = enemy.getBoundingBox();
		double insetX = box.getXsize() * 0.25;
		double insetY = box.getYsize() * 0.15;
		double insetZ = box.getZsize() * 0.25;
		Vec3 point = new Vec3(
			Mth.clamp(eye.x, box.minX + insetX, box.maxX - insetX),
			Mth.clamp(eye.y, box.minY + insetY, box.maxY - insetY),
			Mth.clamp(eye.z, box.minZ + insetZ, box.maxZ - insetZ)
		);

		// Other players are drawn a little behind their real position; lead them by a tick.
		Vec3 velocity = enemy.position().subtract(enemy.xo, enemy.yo, enemy.zo);
		this.aimOffset = point.add(velocity).subtract(enemy.position());
	}

	/**
	 * Called every frame: turns the camera towards the aim point. Like Quantum's bot it covers the
	 * remaining angle in {@link #aimLag} ticks (1 = instantly).
	 */
	public void onFrame(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		LivingEntity enemy = this.target;
		Vec3 offset = this.aimOffset;
		if (!(this.active && this.engaged) || this.utilities.isBusy() || player == null || enemy == null || offset == null) {
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

		double perTick = 1.0 / this.aimLag;
		double follow = perTick >= 1.0 ? 1.0 : 1.0 - Math.pow(1.0 - perTick, seconds * 20.0);
		float yawStep = (float) (Mth.wrapDegrees(wantedYaw - player.getYRot()) * follow);
		float pitchStep = (float) ((wantedPitch - player.getXRot()) * follow);

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

	static void click(KeyMapping key) {
		KeyMappingAccessor accessor = (KeyMappingAccessor) key;
		accessor.qwantium$setClickCount(accessor.qwantium$getClickCount() + 1);
	}
}
