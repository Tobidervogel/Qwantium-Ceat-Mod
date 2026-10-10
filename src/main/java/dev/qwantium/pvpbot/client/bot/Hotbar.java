package dev.qwantium.pvpbot.client.bot;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Finds useful items in the hotbar. The bot only uses the hotbar, like a player in a fight would. */
final class Hotbar {
	private static final Set<Item> BAD_FOOD = Set.of(
		Items.ROTTEN_FLESH,
		Items.SPIDER_EYE,
		Items.POISONOUS_POTATO,
		Items.PUFFERFISH,
		Items.CHORUS_FRUIT,
		Items.SUSPICIOUS_STEW,
		Items.GOLDEN_APPLE,
		Items.ENCHANTED_GOLDEN_APPLE
	);

	private Hotbar() {
	}

	/** Slot of the melee weapon with the best damage per second, or -1. */
	static int bestWeapon(Player player) {
		Inventory inventory = player.getInventory();
		int best = -1;
		double bestScore = 0.0;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!isMeleeWeapon(stack)) {
				continue;
			}

			ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
			double damage = modifiers.compute(Attributes.ATTACK_DAMAGE, 1.0, EquipmentSlot.MAINHAND);
			double speed = modifiers.compute(Attributes.ATTACK_SPEED, 4.0, EquipmentSlot.MAINHAND);
			double score = damage * speed;
			if (score > bestScore) {
				bestScore = score;
				best = slot;
			}
		}
		return best;
	}

	static int find(Player player, Item item) {
		return find(player, stack -> stack.is(item));
	}

	static int findAxe(Player player) {
		return find(player, stack -> stack.is(ItemTags.AXES));
	}

	static int findGoldenApple(Player player) {
		int slot = find(player, stack -> stack.is(Items.GOLDEN_APPLE));
		return slot >= 0 ? slot : find(player, stack -> stack.is(Items.ENCHANTED_GOLDEN_APPLE));
	}

	static int findFood(Player player) {
		return find(player, stack -> stack.has(DataComponents.FOOD) && !BAD_FOOD.contains(stack.getItem()));
	}

	static int findTotem(Player player) {
		return find(player, stack -> stack.has(DataComponents.DEATH_PROTECTION));
	}

	static int findShield(Player player) {
		return find(player, stack -> stack.has(DataComponents.BLOCKS_ATTACKS));
	}

	static boolean isMeleeWeapon(ItemStack stack) {
		return (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) && !stack.has(DataComponents.PIERCING_WEAPON);
	}

	private static int find(Player player, Predicate<ItemStack> predicate) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && predicate.test(stack)) {
				return slot;
			}
		}
		return -1;
	}
}
