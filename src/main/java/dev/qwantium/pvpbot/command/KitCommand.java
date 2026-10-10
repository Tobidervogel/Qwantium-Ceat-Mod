package dev.qwantium.pvpbot.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * {@code /qwantiumkit}: replaces your inventory with the sword kit Quantum's bot fights with
 * (diamond gear), so the bot can use all of its tricks in your own world. Needs cheats / op.
 */
public final class KitCommand {
	private KitCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("qwantiumkit")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(context -> give(context.getSource())));
	}

	private static int give(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HolderLookup.RegistryLookup<Enchantment> enchantments = source.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		Inventory inventory = player.getInventory();
		inventory.clearContent();

		player.setItemSlot(EquipmentSlot.HEAD, armor(Items.DIAMOND_HELMET, enchantments));
		player.setItemSlot(EquipmentSlot.CHEST, armor(Items.DIAMOND_CHESTPLATE, enchantments));
		player.setItemSlot(EquipmentSlot.LEGS, armor(Items.DIAMOND_LEGGINGS, enchantments));
		ItemStack boots = armor(Items.DIAMOND_BOOTS, enchantments);
		enchant(boots, enchantments, Enchantments.FEATHER_FALLING, 4);
		player.setItemSlot(EquipmentSlot.FEET, boots);
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));

		// Same hotbar layout as Quantum's bot.
		inventory.setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
		inventory.setItem(1, new ItemStack(Items.WIND_CHARGE, 64));
		inventory.setItem(2, new ItemStack(Items.COBWEB, 64));
		inventory.setItem(3, unbreakable(new ItemStack(Items.DIAMOND_SWORD)));
		inventory.setItem(4, new ItemStack(Items.GOLDEN_APPLE, 64));
		inventory.setItem(5, unbreakable(new ItemStack(Items.DIAMOND_AXE)));
		inventory.setItem(6, new ItemStack(Items.ENDER_PEARL, 16));
		inventory.setItem(7, new ItemStack(Items.WATER_BUCKET));
		inventory.setItem(8, new ItemStack(Items.LAVA_BUCKET));
		// Spares in the inventory, as in Quantum's kit.
		inventory.setItem(11, new ItemStack(Items.WATER_BUCKET));
		inventory.setItem(12, new ItemStack(Items.WATER_BUCKET));
		inventory.setItem(14, new ItemStack(Items.WATER_BUCKET));
		inventory.setItem(16, new ItemStack(Items.LAVA_BUCKET));
		inventory.setItem(17, new ItemStack(Items.TOTEM_OF_UNDYING));
		inventory.setItem(18, new ItemStack(Items.TOTEM_OF_UNDYING));
		inventory.setSelectedSlot(3);
		player.containerMenu.broadcastChanges();

		source.sendSuccess(() -> Component.translatable("qwantiumbot.kit.given").withStyle(ChatFormatting.GREEN), false);
		return Command.SINGLE_SUCCESS;
	}

	private static ItemStack armor(Item item, HolderLookup.RegistryLookup<Enchantment> enchantments) {
		ItemStack stack = unbreakable(new ItemStack(item));
		enchant(stack, enchantments, Enchantments.PROTECTION, 4);
		return stack;
	}

	private static ItemStack unbreakable(ItemStack stack) {
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		return stack;
	}

	private static void enchant(ItemStack stack, HolderLookup.RegistryLookup<Enchantment> enchantments, ResourceKey<Enchantment> key, int level) {
		stack.enchant(enchantments.getOrThrow(key), level);
	}
}
