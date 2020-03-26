package com.vicr123.bnbshops;

import com.vicr123.bnbshops.Commands.ShopCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class BnbShops extends JavaPlugin {
    LWCManager lwc;
    Connection connection;
    PlayerState state;
    ShopsApi api;

    @Override
    public void onDisable() {

    }

    @Override
    public void onEnable() {
        lwc = new LWCManager();
        state = new PlayerState();
        api = new ShopsApi(this);
        prepareDatabase();

        Bukkit.getServer().getPluginManager().registerEvents(new Events(this), this);
        this.getCommand("shop").setExecutor(new ShopCommand(this));
    }

    public Connection db() {
        return this.connection;
    }

    public PlayerState playerState() {
        return this.state;
    }

    public LWCManager lwc() {
        return lwc;
    }

    public ShopsApi api() {
        return this.api;
    }

    private void prepareDatabase() {
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:shops.db");
            this.connection.createStatement().execute("PRAGMA foreign_keys=ON");
            this.connection.createStatement().execute("CREATE TABLE IF NOT EXISTS shops(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, owner TEXT, world TEXT," +
                                                             "UNIQUE(name, owner))");
            this.connection.createStatement().execute("CREATE TABLE IF NOT EXISTS defaultShop(shopId INTEGER, operator TEXT UNIQUE," +
                                                             "FOREIGN KEY (shopId) REFERENCES shops(id) ON DELETE CASCADE)");
            this.connection.createStatement().execute("CREATE TABLE IF NOT EXISTS chests(shopId INTEGER, x INTEGER, y INTEGER, z INTEGER," +
                                                             "PRIMARY KEY (shopId, x, y, z)," +
                                                             "FOREIGN KEY (shopId) REFERENCES shops(id) ON DELETE CASCADE," +
                                                             "UNIQUE(x, y, z))");
            this.connection.createStatement().execute("CREATE TABLE IF NOT EXISTS signs(shopId INTEGER, x INTEGER, y INTEGER, z INTEGER, type INTEGER, item TEXT, amount FLOAT," +
                                                             "PRIMARY KEY (shopId, x, y, z)," +
                                                             "FOREIGN KEY (shopId) REFERENCES shops(id) ON DELETE CASCADE," +
                                                             "UNIQUE(x, y, z))");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
