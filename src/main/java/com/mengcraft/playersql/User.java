package com.mengcraft.playersql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class User {

    private UUID uuid;
    private double health;
    private int food;
    private int hand;
    private int exp;
    private String inventory;
    private String armor;
    private String chest;
    private String effect;
    private boolean locked;
    private int lastUpdate;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public double getHealth() {
        return health;
    }

    public void setHealth(double health) {
        this.health = health;
    }

    public int getFood() {
        return food;
    }

    public void setFood(int food) {
        this.food = food;
    }

    public int getHand() {
        return hand;
    }

    public void setHand(int hand) {
        this.hand = hand;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public String getInventory() {
        return inventory;
    }

    public void setInventory(String inventory) {
        this.inventory = inventory;
    }

    public String getArmor() {
        return armor;
    }

    public void setArmor(String armor) {
        this.armor = armor;
    }

    public String getChest() {
        return chest;
    }

    public void setChest(String chest) {
        this.chest = chest;
    }

    public String getEffect() {
        return effect;
    }

    public void setEffect(String effect) {
        this.effect = effect;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public int getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(int lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public void save() {
        setLastUpdate((int) (System.currentTimeMillis() / 1000));
        PluginMain.getDB().queryUpdate("UPDATE playerData SET health = ?, food = ?, hand = ?, exp = ?, inventory = ?, armor = ?, chest = ?, effect = ?, locked = ?, last_update = ? WHERE uuid = ?",
                                       getHealth(), getFood(), getHand(), getExp(), getInventory(), getArmor(), getChest(), getEffect(), isLocked(), getLastUpdate(), getUuid().toString());
    }


    public static User get(UUID uuid) {
        try {
            ResultSet rs = PluginMain.getDB().querySelect("SELECT * FROM playerData WHERE uuid = ?", uuid.toString());
            if (rs == null) return get(uuid);
            if (!rs.next()) return null;
            User user = new User();
            user.setUuid(UUID.fromString(rs.getString("uuid")));
            user.setHealth(rs.getDouble("health"));
            user.setFood(rs.getInt("food"));
            user.setHand(rs.getInt("hand"));
            user.setExp(rs.getInt("exp"));
            user.setInventory(rs.getString("inventory"));
            user.setArmor(rs.getString("armor"));
            user.setChest(rs.getString("chest"));
            user.setEffect(rs.getString("effect"));
            user.setLocked(rs.getBoolean("locked"));
            user.setLastUpdate(rs.getInt("last_update"));
            return user;
        } catch (SQLException ex) {
            Logger.getLogger(User.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static User insert(UUID uuid) {
        PluginMain.getDB().queryUpdate("INSERT INTO playerData (uuid, inventory, armor, chest, effect, last_update) VALUES (?, '', '', '', '', ?)", uuid.toString(), (int) (System.currentTimeMillis() / 1000));
        return get(uuid);
    }
}
