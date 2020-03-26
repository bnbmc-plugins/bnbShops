package com.vicr123.bnbshops;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;

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
                if (b.getType() == Material.CHEST) {
                    onChestClick(b, e.getPlayer());
                    e.setCancelled(true);
                } else if (Arrays.asList(Material.ACACIA_SIGN, Material.ACACIA_WALL_SIGN, Material.BIRCH_SIGN, Material.BIRCH_WALL_SIGN,
                        Material.DARK_OAK_SIGN, Material.DARK_OAK_WALL_SIGN, Material.JUNGLE_SIGN, Material.JUNGLE_WALL_SIGN,
                        Material.OAK_SIGN, Material.OAK_WALL_SIGN, Material.SPRUCE_SIGN, Material.SPRUCE_WALL_SIGN).contains(b.getType())) {
                    onSignClick(b, e.getPlayer());
                    e.setCancelled(true);
                }
            }
        } catch (SQLException exception) {
            e.getPlayer().sendMessage("Sorry, a database error occurred. Contact an admin immediately.");
            exception.printStackTrace();
        }
    }

    private void onChestClick(Block b, Player player) throws SQLException {
        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_CHEST) return;

        Chest chest = (Chest) b.getState();
        int store = plugin.api().getDefaultShop(player);
        boolean added;
        if (chest.getInventory().getHolder() instanceof DoubleChest) {
            DoubleChest dc = (DoubleChest) chest.getInventory().getHolder();

            added = plugin.api().toggleChestInShop(store, (Chest) dc.getLeftSide());
            plugin.api().toggleChestInShop(store, (Chest) dc.getRightSide());
        } else {
            added = plugin.api().toggleChestInShop(store, chest);
        }

        if (added) {
            player.sendMessage(ChatColor.GREEN + "We've added that chest to your shop");
        } else {
            player.sendMessage(ChatColor.GREEN + "We've removed that chest from your shop");
        }
    }

    private void onSignClick(Block b, Player player) throws SQLException {
        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_SIGN) return;

        Sign sign = ((Sign) b.getState());
        Integer shop = plugin.api().shopForSign(sign);

        int store = plugin.api().getDefaultShop(player);
        ShopsApi.SignType signType;
        if (sign.getLine(0).equals("[Buy]")) {
            signType = ShopsApi.SignType.SIGN_TYPE_BUY;
        } else if (sign.getLine(0).equals("[Sell]")) {
            signType = ShopsApi.SignType.SIGN_TYPE_SELL;
        } else {
            player.sendMessage(ChatColor.RED + "That sign isn't correctly formatted.");
            player.sendMessage(ChatColor.RED + "Write [Buy] or [Sell] on the first line.");
            return;
        }

        String value = sign.getLine(3);
        float amount;
        try {
            if (value.startsWith("$")) {
                amount = Float.parseFloat(value.substring(1, value.length()));
            } else {
                amount = Float.parseFloat(value);
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "That sign isn't correctly formatted.");
            player.sendMessage(ChatColor.RED + "Write the amount to sell these items for on the fourth line.");
            return;
        }

        PlayerState.SignMetadata items = plugin.playerState().getSignModifyState(player);

        plugin.api().addSignToShop(store, sign, signType, items.item, amount);
        player.sendMessage(ChatColor.GREEN + "You've put " + items.item.getAmount() + " " + items.item.getType().toString() + " on sale for " + amount + "!");

        plugin.playerState().clearUserState(player);

        if (signType == ShopsApi.SignType.SIGN_TYPE_BUY) {
            sign.setLine(0, ChatColor.BLUE + "[Buy]");
        } else {
            sign.setLine(0, ChatColor.BLUE + "[Sell]");
        }
        sign.setLine(3, "$" + amount);
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
