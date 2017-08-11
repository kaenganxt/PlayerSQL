package com.mengcraft.playersql;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MySQL {

    private Connection conn;
    private final FileConfiguration config;
    private boolean reconnecting = false;
    private final String db;
    private final PluginMain main;

    public MySQL(PluginMain main, String dbName) {
        db = dbName;
        this.main = main;
        File file = new File("plugins/database/", "db_" + dbName + ".yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        cfg.addDefault("host", "localhost");
        cfg.addDefault("port", 3306);
        cfg.addDefault("username", "username");
        cfg.addDefault("pw", "pw");
        cfg.options().copyDefaults(true);
        try {
            cfg.save(file);
        } catch (IOException e) {
        }
        this.config = cfg;
        if (!this.openConnection()) {
            System.err.println("PlayerSQL: No DB connection");
        }
    }

    @Deprecated
    public String escapeString(String text) {
        return text == null ? "" : text.replace("\\", "").replace("'", "\\'");
    }

    private boolean openConnection() {
        try {
            String host = config.getString("host");
            int port = config.getInt("port");
            String username = config.getString("username");
            String pw = config.getString("pw");
            String dataB = config.getString("db", db);
            Connection connLoc = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + dataB + "?serverTimezone=Europe/Berlin", username, pw);
            this.conn = connLoc;
            return true;
        } catch (SQLException e) {
            System.err.println(e.getLocalizedMessage());
            return false;
        }
    }

    public boolean hasConnection() {
        try {
            boolean validConn = true;
            if (this.conn == null) {
                validConn = false;
            } else if (!this.conn.isValid(1)) {
                validConn = false;
            }
            return validConn;
        } catch (SQLException e) {
            return false;
        }
    }

    public void queryUpdate(boolean async, String query, Object... args) {
        if (async) main.runTaskAsynchronously(() -> queryUpdate(query, args));
        else queryUpdate(query, args);
    }

    public void queryUpdate(String query, Object... args) {
        if (!hasConnection()) {
            queryRedo(query, args);
            return;
        }
        Connection connLoc = conn;
        PreparedStatement st = null;
        try {
            st = connLoc.prepareStatement(query);
            int i = 1;
            for (Object o : args) {
                st.setObject(i, o);
                i++;
            }
            st.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to send Update '" + query + "'! (" + e.getLocalizedMessage() + ")");
        }
        closeRessources(st);
    }

    public ResultSet querySelect(String query, Object... args) {
        if (!hasConnection()) {
            int count = 0;
            while (!reconnect()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(MySQL.class.getName()).log(Level.SEVERE, null, ex);
                }
                count++;
                if (count == 100) return null;
            }
        }
        try {
            PreparedStatement st = conn.prepareStatement(query);
            int i = 1;
            for (Object o : args) {
                st.setObject(i, o);
                i++;
            }
            return querySelect(st);
        } catch (SQLException ex) {
            System.err.println("Error trying to build Prepared Statement: " + ex.getLocalizedMessage());
        }
        return null;
    }

    private ResultSet querySelect(PreparedStatement st) {
        ResultSet rs;
        try {
            rs = st.executeQuery();
        } catch (SQLException e) {
            System.err.println("Failed to send 'SELECT'-Query!(" + st.toString() + ")");
            System.err.println("Caused by: " + e.getMessage());
            return null;
        }
        return rs;
    }

    private boolean reconnect() {
        if (reconnecting) {
            return false;
        }
        reconnecting = true;
        System.out.println("Reconnecting...");
        closeConnection();
        if (!openConnection()) {

        } else {
            System.out.println("Database reconnect successful!");
        }
        reconnecting = false;
        return true;
    }

    private void queryRedo(String query, Object... args) {
        if (!reconnect()) {
            Bukkit.getScheduler().runTaskLaterAsynchronously(main, () -> {
                queryUpdate(query, args);
            }, 5);
        } else {
            queryUpdate(query, args);
        }
    }

    private void closeRessources(PreparedStatement st) {
        if (st != null) {
            try {
                st.close();
            } catch (SQLException e) {}
        }
    }

    public void closeConnection() {
        try {
            if (this.conn != null) {
                this.conn.close();
            }
        } catch (SQLException e) {
        } finally {
            this.conn = null;
        }
    }
}
