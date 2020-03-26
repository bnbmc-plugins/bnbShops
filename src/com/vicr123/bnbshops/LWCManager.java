package com.vicr123.bnbshops;

import org.bukkit.Location;

import java.sql.*;

public class LWCManager {
    boolean lwcAvailable = true;
    Connection db;

    LWCManager() {
        try {
            db = DriverManager.getConnection("jdbc:sqlite:plugins/LWC/lwc.db");
        } catch (SQLException e) {
            lwcAvailable = false;
        }
    }

    String blockProtection(Location l) throws SQLException {
        return this.blockProtection(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    String blockProtection(String world, int x, int y, int z) throws SQLException {
        if (!lwcAvailable) return "";

        PreparedStatement statement = db.prepareStatement("SELECT owner FROM lwc_protections WHERE x=? AND y=? AND z=? AND world=?");
        statement.setInt(1, x);
        statement.setInt(2, y);
        statement.setInt(3, z);
        statement.setString(4, world);

        ResultSet results = statement.executeQuery();
        if (!results.next()) return "";

        String owner = results.getString(1);
        return owner;
    }
}
