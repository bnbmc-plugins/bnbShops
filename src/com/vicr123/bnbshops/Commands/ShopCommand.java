package com.vicr123.bnbshops.Commands;

import com.vicr123.bnbshops.BnbShops;
import com.vicr123.bnbshops.PlayerState;
import com.vicr123.bnbshops.ShopsApi;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

public class ShopCommand implements CommandExecutor {
    BnbShops plugin;
    ShopsApi api;

    public ShopCommand(BnbShops plugin) {
        this.plugin = plugin;

        this.api = plugin.api();
    }

    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String s, String[] strings) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage("Sorry, this command is only usable as a player.");
            return true;
        }

        if (strings.length < 1) return false;

        try {
            switch (strings[0].toLowerCase()) {
                case "create":
                    return this.createShop((Player) commandSender, strings);
                case "change":
                case "switch":
                    return this.changeShop((Player) commandSender, strings);
                case "current":
                    return this.currentShop((Player) commandSender, strings);
                case "addchests":
                case "editchests":
                    return this.addChests((Player) commandSender, strings);
                case "setsign":
                    return this.addSign((Player) commandSender, strings);
                case "delete":
                    return this.deleteShop((Player) commandSender, strings);
                case "done":
                    return this.clearPlayerState((Player) commandSender, strings);
            }
        } catch (SQLException exception) {
            commandSender.sendMessage("Sorry, a database error occurred. Contact an admin immediately.");
            exception.printStackTrace();
        }
        return true;
    }

    private boolean createShop(Player player, String[] strings) throws SQLException {
        if (strings.length < 2) return false;

        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        String shopName = String.join(" ", Arrays.asList(strings).subList(1, strings.length));
        api.createShop(shopName, player, player.getLocation().getWorld());

        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "SHOP CREATED!");
        player.sendMessage(ChatColor.GREEN + "Shop name: " + shopName);
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "NEXT STEPS");
        player.sendMessage("1. Register a chest");
        player.sendMessage(ChatColor.GOLD + "  /shop addChests");
        player.sendMessage("2. Create Signs");
        player.sendMessage(ChatColor.GOLD + "  Put a sign down and type on the first line:");
        player.sendMessage(ChatColor.AQUA + "    [Buy] " + ChatColor.GOLD + "to sell items");
        player.sendMessage(ChatColor.AQUA + "    [Sell] " + ChatColor.GOLD + "to obtain items from another player");
        player.sendMessage(ChatColor.GOLD + "  You can put any text on the second and third line");
        player.sendMessage(ChatColor.GOLD + "  but it's recommended to put the item name and quantity there.");
        player.sendMessage("3. Link Signs");
        player.sendMessage(ChatColor.GOLD + "  To sell a carrot:");
        player.sendMessage(ChatColor.GOLD + "    /shop setSign carrot");
        player.sendMessage(ChatColor.GOLD + "  To sell an item in your hand:");
        player.sendMessage(ChatColor.GOLD + "    /shop setSign :hand:");
        player.sendMessage(ChatColor.GOLD + "  To sell 64 carrots:");
        player.sendMessage(ChatColor.GOLD + "    /shop setSign carrot 64");

        return true;
    }

    private boolean changeShop(Player player, String[] strings) throws SQLException {
        if (strings.length < 2) return false;

        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        String shopName = String.join(" ", Arrays.asList(strings).subList(1, strings.length));
        Integer shopId = api.getShopId(player, shopName);

        if (shopId == null) {
            player.sendMessage(ChatColor.RED + "Sorry, that shop doesn't exist. You can create it with /shop create");
            return true;
        }

        api.setDefaultShop(player, shopId);
        player.sendMessage(ChatColor.GREEN + "You've switched to the shop \"" + shopName + "\"");
        return true;
    }

    private boolean currentShop(Player player, String[] strings) throws SQLException {
        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        Integer shopId = api.getDefaultShop(player);
        if (shopId == null) {
            player.sendMessage(ChatColor.RED + "Sorry, you have no shops. You can create one with /shop create");
            return true;
        }

        String shopName = api.getShopName(shopId);
        player.sendMessage(ChatColor.GREEN + "Any actions you perform will take place on the shop \"" + shopName + "\"");
        return true;
    }

    private boolean deleteShop(Player player, String[] strings) throws SQLException {
        if (strings.length < 2) return false;

        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        String shopName = String.join(" ", Arrays.asList(strings).subList(1, strings.length));
        api.deleteShop(shopName, player);

        player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "SHOP REMOVED!");
        player.sendMessage(ChatColor.GREEN + "Shop name: " + shopName);

        return true;
    }

    private boolean addChests(Player player, String[] strings) throws SQLException {
        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        Integer shopId = api.getDefaultShop(player);
        if (shopId == null) {
            player.sendMessage(ChatColor.RED + "Select a shop to add chests to with /shop switch");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Click on a chest to add or remove it from " + api.getShopName(shopId) + ".");
        player.sendMessage(ChatColor.GREEN + "Once you're done, use /shop done");
        plugin.playerState().setUserState(player, PlayerState.State.PLAYER_STATE_CHEST);

        return true;
    }

    private boolean clearPlayerState(Player player, String[] strings) {
        plugin.playerState().clearUserState(player);
        switch (plugin.playerState().playerState(player)) {
            case PLAYER_STATE_IDLE:
                player.sendMessage(ChatColor.RED + "You're not editing anything at the moment.");
                return true;
            case PLAYER_STATE_CHEST:
                player.sendMessage(ChatColor.GREEN + "Finished adding chests");
                return true;
            case PLAYER_STATE_SIGN:
                player.sendMessage(ChatColor.GREEN + "Finished modifying signs");
                return true;
        }

        return true;
    }

    private boolean addSign(Player player, String[] strings) throws SQLException {
        if (strings.length < 2) return false;

        Material material;
        int amount = 1;

        if (strings[1].equals(":hand:")) {
            material = player.getInventory().getItemInMainHand().getType();
        } else {
            try {
                material = Material.valueOf(strings[1]);
            } catch(IllegalArgumentException e){
                player.sendMessage(ChatColor.RED + "Sorry, that's not a valid item.");
                return true;
            }
        }

        if (strings.length > 2) {
            try {
                amount = Integer.parseInt(strings[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Sorry, that's not a valid number.");
                return true;
            }
        }

        ItemStack items = new ItemStack(material, amount);

        if (plugin.playerState().playerState(player) != PlayerState.State.PLAYER_STATE_IDLE) {
            player.sendMessage(ChatColor.RED + "Cancel the current operation with /shop done first.");
            return true;
        }

        Integer shopId = api.getDefaultShop(player);
        if (shopId == null) {
            player.sendMessage(ChatColor.RED + "Select a shop to add signs to with /shop switch");
            return true;
        }

        PlayerState.SignMetadata data = new PlayerState.SignMetadata();
        data.item = items;

        player.sendMessage(ChatColor.GREEN + "Click on a correctly formatted sign to add or remove it from " + api.getShopName(shopId) + ".");
        player.sendMessage(ChatColor.GREEN + "Once you're done, use /shop done");
        plugin.playerState().setSignModifyState(player, data);

        return true;
    }
}
