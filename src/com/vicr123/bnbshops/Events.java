package com.vicr123.bnbshops;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import tech.cheating.chaireco.IEconomy;
import tech.cheating.chaireco.exceptions.EconomyBalanceTooLowException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Events implements Listener {
    BnbShops plugin;
    Events(BnbShops plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent e) {
        try {
            if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
                Block b = e.getClickedBlock();
                if (b.getState() instanceof Chest) {
                    onChestClick(b, e.getPlayer(), e);
                } else if (b.getState() instanceof Sign) {
                    onSignClick(b, e.getPlayer(), e);
                }
            } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
                Block b = e.getClickedBlock();
                if (b.getState() instanceof Sign) {
                    onSignRightClick(b, e.getPlayer(), e);
                }
            }
        } catch (SQLException exception) {
            e.getPlayer().sendMessage("Sorry, a database error occurred. Contact an admin immediately.");
            exception.printStackTrace();
        }
    }

    private void onChestClick(Block b, Player player, Cancellable e) throws SQLException {
        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_CHEST) return;
        e.setCancelled(true);

        Chest chest = (Chest) b.getState();

        int store = plugin.api().getDefaultShop(player);
        boolean added;

        try {
            if (chest.getInventory().getHolder() instanceof DoubleChest) {
                DoubleChest dc = (DoubleChest) chest.getInventory().getHolder();

                added = plugin.api().toggleChestInShop(store, (Chest) dc.getLeftSide());
                plugin.api().toggleChestInShop(store, (Chest) dc.getRightSide());
            } else {
                added = plugin.api().toggleChestInShop(store, chest);
            }
        } catch (SQLException exception) {
            if (exception.getErrorCode() == 19) { //SQLITE_CONSTRAINT
                player.sendMessage(ChatColor.RED + "That chest is already linked to another shop.");
                return;
            } else {
                throw exception;
            }
        }

        if (added) {
            player.sendMessage(ChatColor.GREEN + "We've added that chest to your shop");
        } else {
            player.sendMessage(ChatColor.GREEN + "We've removed that chest from your shop");
        }

    }

    private void onSignClick(Block b, Player player, Cancellable e) throws SQLException {
        switch (plugin.playerState().playerState(player)) {
            case PLAYER_STATE_SIGN:
                performAddSign(b, player, e);
                break;
            case PLAYER_STATE_REMOVE_SIGN:
                performRemoveSign(b, player, e);
        }
    }

    private void performAddSign(Block b, Player player, Cancellable e) throws SQLException {
        int store = plugin.api().getDefaultShop(player);

        Sign sign = ((Sign) b.getState());
        Integer existingSignOwner = plugin.api().shopForSign(sign);
        if (existingSignOwner != null && existingSignOwner != store) {
            player.sendMessage(ChatColor.RED + "That sign is already linked to another shop.");
            return;
        }

        ShopsApi.SignType signType;
        String signTypeLine = sign.getLine(0);
        if (signTypeLine.startsWith(ChatColor.DARK_BLUE.toString())) signTypeLine = signTypeLine.substring(ChatColor.DARK_BLUE.toString().length(), signTypeLine.length());
        if (signTypeLine.equals("[Buy]")) {
            signType = ShopsApi.SignType.SIGN_TYPE_BUY;
        } else if (signTypeLine.equals("[Sell]")) {
            signType = ShopsApi.SignType.SIGN_TYPE_SELL;
        } else {
            player.sendMessage(ChatColor.RED + "That sign isn't correctly formatted.");
            player.sendMessage(ChatColor.RED + "Write [Buy] or [Sell] on the first line.");
            e.setCancelled(true);
            return;
        }

        String value = sign.getLine(3);
        int amount;
        try {
            if (value.startsWith("$")) {
                amount = IEconomy.getCentValue(value.substring(1));
            } else {
                amount = IEconomy.getCentValue(value);
            }

            if (amount < 0) {
                player.sendMessage(ChatColor.RED + "You can't buy/sell for a negative amount of money.");
                e.setCancelled(true);
                return;
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(ChatColor.RED + "That sign isn't correctly formatted.");
            player.sendMessage(ChatColor.RED + "Write the amount to sell/buy these items for on the fourth line.");
            e.setCancelled(true);
            return;
        }

        PlayerState.SignMetadata items = plugin.playerState().getSignModifyState(player);

        plugin.api().addSignToShop(store, sign, signType, items.item, amount);
        if (signType == ShopsApi.SignType.SIGN_TYPE_BUY) {
            player.sendMessage(ChatColor.GREEN + "You've put " + items.item.getAmount() + " " + items.item.getType().toString() + " on sale for " + IEconomy.getDollarValue(amount) + "!");
        } else {
            player.sendMessage(ChatColor.GREEN + "You're buying " + items.item.getAmount() + " " + items.item.getType().toString() + " for " + IEconomy.getDollarValue(amount) + "!");
        }

        plugin.playerState().clearUserState(player);

        if (signType == ShopsApi.SignType.SIGN_TYPE_BUY) {
            sign.setLine(0, ChatColor.DARK_BLUE + "[Buy]");
        } else {
            sign.setLine(0, ChatColor.DARK_BLUE + "[Sell]");
        }
        sign.setLine(3, IEconomy.getDollarValue(amount));
        sign.update();
        e.setCancelled(true);
    }

    private void performRemoveSign(Block b, Player player, Cancellable e) throws SQLException {
        e.setCancelled(true);

        int store = plugin.api().getDefaultShop(player);

        Sign sign = ((Sign) b.getState());
        Integer existingSignOwner = plugin.api().shopForSign(sign);
        if (existingSignOwner == null || existingSignOwner != store) {
            player.sendMessage(ChatColor.RED + "That sign is not linked to " + plugin.api().getShopName(store));
            return;
        }

        plugin.api().removeSignFromShop(sign);
        player.sendMessage(ChatColor.GREEN + "That sign was removed from " + plugin.api().getShopName(store));
        plugin.playerState().clearUserState(player);
    }

    private void onSignRightClick(Block b, Player player, Cancellable e) throws SQLException {
        Sign sign = ((Sign) b.getState());

        Integer shop = plugin.api().shopForSign(sign);
        if (shop == null) return; //This sign does not belong to a shop

        e.setCancelled(true);

        ItemStack items = plugin.api().itemsForSign(sign);
        ShopsApi.SignType type = plugin.api().typeForSign(sign);
        int price = plugin.api().priceForSign(sign);

        if (type == ShopsApi.SignType.SIGN_TYPE_SELL) {
            performRefund(player, shop, items, price);
        } else {
            performSale(player, shop, items, price);
        }
    }

    private void performSale(Player target, int shopId, ItemStack items, int price) throws SQLException {
        Chest[] chests = plugin.api().chestsForShop(shopId);
        String shopName = plugin.api().getShopName(shopId);
        String shopOwner = plugin.api().getShopOwner(shopId);

        int initialAmount = items.getAmount();

        if (price > 0) {
            int balance = plugin.economy().getBalance(target);
            if (balance < price) {
                target.sendMessage(ChatColor.RED + "You don't have enough money to buy this item.");
                return;
            }
        }

        //Make sure the player has enough inventory space
        HashMap<Integer, ItemStack> result = target.getInventory().addItem(items);
        if (result.size() != 0) {
            //Roll back the player
            items.setAmount(initialAmount - result.get(0).getAmount());
            target.getInventory().removeItem(items);

            target.sendMessage(ChatColor.RED + "You don't have enough inventory space to buy this item.");
            return;
        }

        int remaining = removeFromChests(chests, items);
        if (remaining != 0) { //No stock left
            //Roll back the chests
            items.setAmount(initialAmount - remaining);
            addToChests(chests, items);

            //Roll back the player
            items.setAmount(initialAmount);
            target.getInventory().removeItem(items);

            target.sendMessage(ChatColor.RED + shopName + " is out of stock.");
            return;
        }

        //Don't bother with a transfer if the item is priced at $0
        if (price > 0) {
            try {
                plugin.economy().transfer(target.getUniqueId().toString(), target.getName(), shopOwner, shopName, price, "Purchase of " + initialAmount + " " + items.getType().toString());
            } catch (EconomyBalanceTooLowException e) {
                e.printStackTrace();
            }
        }

        target.sendMessage(ChatColor.GREEN + "You've purchased " + initialAmount + " " + items.getType().toString() + " from " + shopName + " for " + IEconomy.getDollarValue(price) + "!");
    }

    private void performRefund(Player target, int shopId, ItemStack items, int price) throws SQLException {
        //Make sure the player has enough inventory
        if (!target.getInventory().containsAtLeast(items, items.getAmount())) {
            target.sendMessage(ChatColor.RED + "You don't have enough items to sell.");
            return;
        }

        Chest[] chests = plugin.api().chestsForShop(shopId);
        String shopName = plugin.api().getShopName(shopId);
        String shopOwner = plugin.api().getShopOwner(shopId);

        if (price > 0) {
            int balance = plugin.economy().getBalance(target);
            if (balance < price) {
                target.sendMessage(ChatColor.RED + shopName + "doesn't have enough money to buy this item from you.");
                return;
            }
        }

        int initialAmount = items.getAmount();
        int remaining = addToChests(chests, items);

        if (remaining != 0) { //No space left in the chests
            //Roll back the chests
            items.setAmount(initialAmount - remaining);
            removeFromChests(chests, items);

            target.sendMessage(ChatColor.RED + shopName + " doesn't have enough space for your items.");
            return;
        }

        //Don't bother with a transfer if the item is priced at $0
        if (price > 0) {
            try {
                plugin.economy().transfer(shopOwner, shopName, target.getUniqueId().toString(), target.getName(), price, "Refund for " + initialAmount + " " + items.getType().toString());
            } catch (EconomyBalanceTooLowException e) {
                e.printStackTrace();
            }
        }

        target.getInventory().remove(items);
        target.sendMessage(ChatColor.GREEN + "You've sold " + initialAmount + " " + items.getType().toString() + " to " + shopName + " for " + IEconomy.getDollarValue(price) + "!");
    }

    private int addToChests(Chest[] chests, ItemStack items) {
        for (Chest chest : chests) {
            HashMap<Integer, ItemStack> result = chest.getInventory().addItem(items);
            if (result.size() == 0) { //We're done!
                return 0;
            }
            items.setAmount(result.get(0).getAmount());
        }
        return items.getAmount();
    }

    private int removeFromChests(Chest[] chests, ItemStack items) {
        for (Chest chest : chests) {
            HashMap<Integer, ItemStack> result = chest.getInventory().removeItem(items);
            if (result.size() == 0) { //We're done!
                return 0;
            }
            items.setAmount(result.get(0).getAmount());
        }
        return items.getAmount();
    }

    @EventHandler
    public void onBlockCreate(BlockPlaceEvent e) {
        try {
            if (e.getBlock().getType() == Material.CHEST) {
                Chest chest = (Chest) e.getBlock().getState();

                if (chest.getInventory().getHolder() instanceof DoubleChest) {
                    DoubleChest dc = (DoubleChest) chest.getInventory().getHolder();

                    Chest oldChest;
                    if (dc.getLeftSide() == chest) {
                        oldChest = (Chest) dc.getRightSide();
                    } else {
                        oldChest = (Chest) dc.getLeftSide();
                    }

                    Integer oldShopId = plugin.api().shopForChest(oldChest);
                    if (oldShopId == null) return; //This chest is not part of a shop

                    String owner = plugin.api().getShopOwner(oldShopId);
                    if (!owner.equals(e.getPlayer().getUniqueId().toString())) {
                        e.setCancelled(true);

                        e.getPlayer().sendMessage(ChatColor.RED + "Sorry, you can't place that chest here because it will modify an existing shop that you're not a part of.");
                    }

                    plugin.api().addChestToShop(oldShopId, chest);
                }
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        try {
            if (e.getBlock().getType() == Material.CHEST) {
                Integer shopId = plugin.api().shopForChest((Chest) e.getBlock().getState());
                if (shopId == null) return;

                String owner = plugin.api().getShopOwner(shopId);
                if (!owner.equals(e.getPlayer().getUniqueId().toString()) && !e.getPlayer().hasPermission("bnbshop.breakOtherChests")) {
                    e.setCancelled(true);

                    e.getPlayer().sendMessage(ChatColor.RED + "Sorry, you can't break this chest because it is linked to a shop that is not yours.");
                    return;
                }

                plugin.api().removeChestFromShop((Chest) e.getBlock().getState());
            } else if (Arrays.asList(Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN, Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
                    Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN, Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN,
                    Material.OAK_SIGN, Material.OAK_WALL_SIGN, Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN).contains(e.getBlock().getType())) {

                Sign sign = ((Sign) e.getBlock().getState());

                Integer shopId = plugin.api().shopForSign(sign);
                if (shopId == null) return; //This sign is not part of a shop

                String owner = plugin.api().getShopOwner(shopId);
                if (!owner.equals(e.getPlayer().getUniqueId().toString()) && !e.getPlayer().hasPermission("bnbshop.breakOtherSigns")) {
                    e.setCancelled(true);

                    e.getPlayer().sendMessage(ChatColor.RED + "Sorry, you can't break this sign because it is linked to a shop that is not yours.");
                    return;
                }

                plugin.api().removeSignFromShop(sign);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    @EventHandler
    public void onBlockExplode(EntityExplodeEvent e) {
        try {
            for (Block b : e.blockList()) {
                if (b.getType() == Material.CHEST) {
                    plugin.api.removeChestFromShop((Chest) b.getState());
                }
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }
}
