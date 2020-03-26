package com.vicr123.bnbshops;

import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import javax.xml.transform.Result;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

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
        statement.setString(3, world.toString());
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

    public String getShopName(int shopId) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("SELECT name FROM shops WHERE id=?");
        statement.setInt(1, shopId);

        ResultSet results = statement.executeQuery();
        if (!results.next()) return "";
        return results.getString("name");
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
        return null;
//        return ItemStack()
    }

    public void addSignToShop(int id, Sign sign, SignType type, ItemStack items, float amount) throws SQLException {
        try {
            PipedOutputStream pipedOutputStream = new PipedOutputStream();
            BukkitObjectOutputStream os = new BukkitObjectOutputStream(new BufferedOutputStream(pipedOutputStream));
            os.writeObject(items);

            System.out.println(items.serialize().toString());
            PreparedStatement statement = plugin.db().prepareStatement("INSERT INTO signs(shopId, x, y, z, type, item, amount) VALUES(?, ?, ?, ?, ?, ?, ?)");
            statement.setInt(1, id);
            statement.setInt(2, sign.getLocation().getBlockX());
            statement.setInt(3, sign.getLocation().getBlockY());
            statement.setInt(4, sign.getLocation().getBlockZ());
            statement.setInt(5, type == SignType.SIGN_TYPE_BUY ? 0 : 1);
            statement.setBinaryStream(6, new PipedInputStream(pipedOutputStream));
            statement.setFloat(7, amount);
            statement.executeUpdate();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void removeSignFromShop(Sign sign) throws SQLException {
        PreparedStatement statement = plugin.db().prepareStatement("DELETE FROM signs WHERE x=? AND y=? AND z=?");
        statement.setInt(1, sign.getLocation().getBlockX());
        statement.setInt(2, sign.getLocation().getBlockY());
        statement.setInt(3, sign.getLocation().getBlockZ());
        statement.executeUpdate();
    }
}
