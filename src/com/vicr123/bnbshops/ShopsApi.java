package com.vicr123.bnbshops;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.xml.transform.Result;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class ShopsApi {
    BnbShops plugin;

    enum SignType {
        SIGN_TYPE_BUY,
        SIGN_TYPE_SELL
    }

    public ShopsApi(BnbShops plugin) {
        this.plugin = plugin;
    }

    public void createShop(String name, Player owner, World world) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("INSERT INTO shops(name, owner, world) VALUES(?, ?, ?)");
        statement.setString(1, name);
        statement.setString(2, owner.getUniqueId().toString());
        statement.setString(3, world.getUID().toString());
        statement.executeUpdate();

        //Set this shop as default
        ResultSet id = plugin.db().createStatement().executeQuery("SELECT last_insert_rowid()");
        id.next();
        setDefaultShop(owner, id.getInt(1));
    }

    public void deleteShop(String name, Player owner) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("DELETE FROM shops WHERE name=? AND owner=?");
        statement.setString(1, name);
        statement.setString(2, owner.getUniqueId().toString());
        statement.executeUpdate();
    }

    public Integer getShopId(Player player, String name) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT id FROM shops WHERE name=? AND owner=?");
        statement.setString(1, name);
        statement.setString(2, player.getUniqueId().toString());
        ResultSet set = statement.executeQuery();

        if (!set.next()) return null;
        return set.getInt("id");
    }

    public UUID getShopWorld(int shopId) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT world FROM shops WHERE id=?");
        statement.setInt(1, shopId);

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return UUID.fromString(results.getString("world"));
    }

    public String getShopName(int shopId) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT name FROM shops WHERE id=?");
        statement.setInt(1, shopId);

        ResultSet results = statement.executeQuery();
        if (!results.next()) return "";
        return results.getString("name");
    }

    public HashMap<Integer, String> shopsForPlayer(Player player) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT id FROM shops WHERE owner=?");
        statement.setString(1, player.getUniqueId().toString());
        ResultSet set = statement.executeQuery();

        HashMap<Integer, String> shops = new HashMap<>();
        while (set.next()) {
            int id = set.getInt("id");
            String shopName = getShopName(id);

            shops.put(id, shopName);
        }
        return shops;
    }

    public void setDefaultShop(Player player, int id) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("INSERT INTO defaultShop(shopId, operator) VALUES(?, ?)" +
                                                                          "ON CONFLICT(operator) DO UPDATE SET shopId=? WHERE operator=?");
        statement.setInt(1, id);
        statement.setString(2, player.getUniqueId().toString());
        statement.setInt(3, id);
        statement.setString(4, player.getUniqueId().toString());
        statement.executeUpdate();
    }

    public Integer getDefaultShop(Player player) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT shopId FROM defaultShop WHERE operator=?");
        statement.setString(1, player.getUniqueId().toString());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return results.getInt("shopid");
    }

    public void addChestToShop(int id, Chest chest) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("INSERT INTO chests(shopId, x, y, z) VALUES(?, ?, ?, ?)");
        statement.setInt(1, id);
        statement.setInt(2, chest.getLocation().getBlockX());
        statement.setInt(3, chest.getLocation().getBlockY());
        statement.setInt(4, chest.getLocation().getBlockZ());
        statement.executeUpdate();
    }

    public boolean toggleChestInShop(int id, Chest chest) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT * FROM chests WHERE shopId=? AND x=? AND y=? AND z=?");
        statement.setInt(1, id);
        statement.setInt(2, chest.getLocation().getBlockX());
        statement.setInt(3, chest.getLocation().getBlockY());
        statement.setInt(4, chest.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (results.next()) {
            removeChestFromShop(chest);
            return false;
        } else {
            addChestToShop(id, chest);
            return true;
        }
    }

    public void removeChestFromShop(Chest chest) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("DELETE FROM chests WHERE x=? AND y=? AND z=?");
        statement.setInt(1, chest.getLocation().getBlockX());
        statement.setInt(2, chest.getLocation().getBlockY());
        statement.setInt(3, chest.getLocation().getBlockZ());
        statement.executeUpdate();
    }

    public Integer shopForChest(Chest chest) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT * FROM chests WHERE x=? AND y=? AND z=?");
        statement.setInt(1, chest.getLocation().getBlockX());
        statement.setInt(2, chest.getLocation().getBlockY());
        statement.setInt(3, chest.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return results.getInt("shopid");
    }

    public Chest[] chestsForShop(int shopId) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT * FROM chests WHERE shopid=?");
        statement.setInt(1, shopId);

        ResultSet results = statement.executeQuery();

        World world = Bukkit.getWorld(this.getShopWorld(shopId));
        ArrayList<Chest> chests = new ArrayList<>();
        while (results.next()) {
            Block block = world.getBlockAt(results.getInt("x"), results.getInt("y"), results.getInt("z"));
            Chunk c = world.getChunkAt(block);

            //Load the chunk if required
            if (!c.isLoaded()) {
                c.load();
            }

            if (!(block.getState() instanceof Chest)) continue; //???

            chests.add((Chest) block.getState());
        }

        Chest[] chestsArray = new Chest[chests.size()];
        chests.toArray(chestsArray);
        return chestsArray;
    }

    public String getShopOwner(int id) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT owner FROM shops WHERE id=?");
        statement.setInt(1, id);

        ResultSet results = statement.executeQuery();
        if (!results.next()) return "";
        return results.getString("owner");
    }

    public Integer shopForSign(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT shopid FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return results.getInt("shopid");
    }

    public ItemStack itemsForSign(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT item FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;

        Material material;
        int amount;

        String[] itemBits = results.getString("item").split(";");
        material = Material.valueOf(itemBits[0]);
        amount = Integer.parseInt(itemBits[1]);

        ItemStack stack = new ItemStack(material, amount);

        if (itemBits.length == 3) {
            String[] metaBits = itemBits[2].split("#");
            switch (metaBits[0]) {
                case "POTION":
                    PotionMeta potion = (PotionMeta) stack.getItemMeta();
                    PotionType type;
                    boolean extended, upgraded;

                    type = PotionType.valueOf(metaBits[1]);
                    extended = metaBits[2].equals("true");
                    upgraded = metaBits[3].equals("true");

                    PotionData data = new PotionData(type, extended, upgraded);
                    potion.setBasePotionData(data);
                    stack.setItemMeta(potion);
                    break;
            }
        }

        return stack;
    }

    public SignType typeForSign(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT type FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return (results.getInt("type") == 0 ? SignType.SIGN_TYPE_BUY : SignType.SIGN_TYPE_SELL);
    }

    public Integer priceForSign(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT amount FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());

        ResultSet results = statement.executeQuery();
        if (!results.next()) return null;
        return results.getInt("amount");
    }

    public void addSignToShop(int id, Sign sign, SignType type, ItemStack items, int amount) throws SQLException {
        String[] itemBits = new String[3];
        itemBits[0] = items.getType().name();
        itemBits[1] = String.valueOf(items.getAmount());
        itemBits[2] = "";

        if (items.getItemMeta() instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) items.getItemMeta();
            PotionData potionData = potion.getBasePotionData();

            String[] metaBits = new String[4];
            metaBits[0] = "POTION";
            metaBits[1] = potionData.getType().toString();
            metaBits[2] = potionData.isExtended() ? "true" : "false";
            metaBits[3] = potionData.isUpgraded() ? "true" : "false";

            System.console().printf(potionData.getType().toString());

            itemBits[2] = String.join("#", metaBits);
        }

        PreparedStatement statement = plugin.db().prepareStatement("REPLACE INTO signs(shopId, x, y, z, type, item, amount) VALUES(?, ?, ?, ?, ?, ?, ?)");
        statement.setInt(1, id);
        statement.setInt(2, sign.getLocation().getBlockX());
        statement.setInt(3, sign.getLocation().getBlockY());
        statement.setInt(4, sign.getLocation().getBlockZ());
        statement.setInt(5, type == SignType.SIGN_TYPE_BUY ? 0 : 1);
        statement.setString(6, String.join(";", itemBits));
        statement.setInt(7, amount);
        statement.executeUpdate();
    }

    public void removeSignFromShop(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("DELETE FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());
        statement.executeUpdate();
    }
}
