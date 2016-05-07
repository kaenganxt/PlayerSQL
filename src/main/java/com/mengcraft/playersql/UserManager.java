package com.mengcraft.playersql;

import com.mengcraft.playersql.lib.ExpUtil;
import com.mengcraft.playersql.lib.ItemUtil;
import com.mengcraft.playersql.lib.JSONUtil;
import com.mengcraft.playersql.task.DailySaveTask;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.json.simple.JSONArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.ChatColor;

/**
 * Created on 16-1-2.
 */
public final class UserManager {

    public static final UserManager INSTANCE = new UserManager();
    public static final ItemStack AIR = new ItemStack(Material.AIR);

    private final Map<UUID, BukkitTask> taskMap;
    private final List<UUID> locked;
    private final Map<UUID, User> cached;
    private final Queue<User> fetched;
    private final HashSet<UUID> newUsers;

    private PluginMain main;
    private ItemUtil itemUtil;
    private ExpUtil expUtil;

    private UserManager() {
        this.taskMap = new ConcurrentHashMap<>();
        this.locked = new ArrayList<>();
        this.cached = new ConcurrentHashMap<>();
        this.fetched = new ConcurrentLinkedQueue<>();
        this.newUsers = new HashSet<>();
    }

    /**
     * @return The user, or <code>null</code> if not exists.
     */
    public User getUser(UUID uuid) {
        return this.cached.get(uuid);
    }

    public void addFetched(User user, boolean isNew) {
        this.fetched.offer(user);
        if (isNew) newUsers.add(user.getUuid());
    }

    public void addFetched(User user) {
        addFetched(user, true);
    }

    /**
     * @return The user, or <code>null</code> if not exists.
     */
    public User fetchUser(UUID uuid) {
        return User.get(uuid);
    }

    /**
     * Create and cache a new user.
     */
    public void create(UUID uuid) {
        User user = User.insert(uuid);
        user.setUuid(uuid);
        user.setLocked(true);
        cacheUser(uuid, user);
    }

    public void cacheUser(UUID uuid, User user) {
        if (user == null) {
            cached.remove(uuid);
        } else {
            cached.put(uuid, user);
        }
    }

    public void saveUser(UUID uuid, boolean lock) {
        if (cached.containsKey(uuid)) {
            saveUser(cached.get(uuid), lock);
        }
    }

    public void saveUser(User user, boolean lock) {
        synchronized (user) {
            if (user.isLocked() != lock) {
                user.setLocked(lock);
            }
        }
        user.save();
        if (Config.DEBUG) {
            this.main.logMessage("Save user data " + user.getUuid() + " done!");
        }
    }

    public void syncUser(User user) {
        syncUser(user, false);
    }

    public void syncUser(User user, Player p, boolean closedInventory) {
        synchronized (user) {
            if (Config.SYN_HEALTH) {
                user.setHealth(p.getHealth());
            }
            if (Config.SYN_FOOD) {
                user.setFood(p.getFoodLevel());
            }
            if (Config.SYN_INVENTORY) {
                if (closedInventory) {
                    p.closeInventory();
                }
                user.setInventory(toString(p.getInventory().getContents()));
                user.setArmor(toString(p.getInventory().getArmorContents()));
                user.setHand(p.getInventory().getHeldItemSlot());
            }
            if (Config.SYN_CHEST) {
                user.setChest(toString(p.getEnderChest().getContents()));
            }
            if (Config.SYN_EFFECT) {
                user.setEffect(toString(p.getActivePotionEffects()));
            }
            if (Config.SYN_EXP) {
                user.setExp(this.expUtil.getExp(p));
            }
        }
    }

    public void syncUser(User user, boolean closedInventory) {
        Player p = main.getPlayer(user.getUuid());
        if (p != null && p.isOnline()) {
            syncUser(user, p, closedInventory);
        }
    }

    public void syncUser(UUID uuid, boolean closedInventory) {
        syncUser(cached.get(uuid), closedInventory);
    }

    public boolean isUserLocked(UUID uuid) {
        return this.locked.indexOf(uuid) != -1;
    }

    public boolean isUserNotLocked(UUID uuid) {
        return this.locked.indexOf(uuid) == -1;
    }

    public void lockUser(UUID uuid) {
        if (!this.locked.contains(uuid)) {
            this.locked.add(uuid);
        }
    }

    public void unlockUser(UUID uuid, boolean scheduled) {
        if (scheduled) {
            this.main.runTask(() -> this.locked.remove(uuid));
        } else {
            if (Config.DEBUG) {
                this.main.logMessage("Unlock user " + uuid + '!');
            }
            this.locked.remove(uuid);
        }
    }

    /**
     * Process fetched users.
     */
    public void pendFetched() {
        while (!this.fetched.isEmpty()) {
            try {
                pend(this.fetched.poll());
            } catch (Exception e) {
                main.logException(e);
            }
        }
    }

    private void pend(User user) {
        Player player = this.main.getPlayer(user.getUuid());
        if (player != null && player.isOnline()) {
            pend(user, player);
        } else this.main.runTaskAsynchronously(() -> {
            if (Config.DEBUG) {
                this.main.logException(new PluginException("User " + user.getUuid() + " not found!"));
            }
            saveUser(user, false);
        });
    }

    private void pend(User polled, Player player) {
        synchronized (polled) {
            if (Config.SYN_INVENTORY) {
                player.closeInventory();
                player.getInventory().setContents(toStack(polled.getInventory()));
                player.getInventory().setArmorContents(toStack(polled.getArmor()));
                player.getInventory().setHeldItemSlot(polled.getHand());
            }
            if (Config.SYN_HEALTH && player.getMaxHealth() >= polled.getHealth()) {
                player.setHealth(polled.getHealth());
            }
            if (Config.SYN_EXP) {
                this.expUtil.setExp(player, polled.getExp());
            }
            if (Config.SYN_FOOD) {
                player.setFoodLevel(polled.getFood());
            }
            if (Config.SYN_EFFECT) {
                for (PotionEffect effect : toEffect(polled.getEffect())) {
                    player.addPotionEffect(effect, true);
                }
            }
            if (Config.SYN_CHEST) {
                player.getEnderChest().setContents(toStack(polled.getChest()));
            }
        }
        createTask(player.getUniqueId());
        unlockUser(player.getUniqueId(), false);
        if (newUsers.contains(player.getUniqueId())) {
            fireSafeLogin(player);
            newUsers.remove(player.getUniqueId());
        }
    }

    public void fireSafeLogin(Player P) {
        SafeLoginEvent event = new SafeLoginEvent(P);
        main.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            String reason = event.getCancelReason();
            P.kickPlayer(reason == null ? ChatColor.RED + "Kicked" : reason);
        }
    }

    @SuppressWarnings("unchecked")
    private List<PotionEffect> toEffect(String data) {
        List<List> parsed = JSONUtil.parseArray(data, JSONUtil.EMPTY_ARRAY);
        List<PotionEffect> output = new ArrayList<>(parsed.size());
        for (List<Number> entry : parsed) {
            output.add(new PotionEffect(PotionEffectType.getById(entry.get(0).intValue()), entry.get(1).intValue(), entry.get(2).intValue()));
        }
        return output;
    }

    @SuppressWarnings("unchecked")
    private ItemStack[] toStack(String data) {
        List<String> parsed = JSONUtil.parseArray(data, JSONUtil.EMPTY_ARRAY);
        List<ItemStack> output = new ArrayList<>(parsed.size());
        for (String s : parsed)
            if (s == null) {
                output.add(AIR);
            } else try {
                output.add(this.itemUtil.convert(s));
            } catch (Exception e) {
                this.main.logException(e);
            }
        return output.toArray(new ItemStack[parsed.size()]);
    }

    @SuppressWarnings("unchecked")
    private String toString(ItemStack[] stacks) {
        JSONArray array = new JSONArray();
        for (ItemStack stack : stacks)
            if (stack == null || stack.getTypeId() == 0) {
                array.add(null);
            } else try {
                array.add(this.itemUtil.convert(stack));
            } catch (Exception e) {
                this.main.logException(e);
            }
        return array.toString();
    }

    @SuppressWarnings("unchecked")
    private String toString(Collection<PotionEffect> effects) {
        JSONArray array = new JSONArray();
        for (PotionEffect effect : effects)
            array.add(new JSONArray() {{
                add(effect.getType().getId());
                add(effect.getDuration());
                add(effect.getAmplifier());
            }});
        return array.toString();
    }

    public void cancelTask(int i) {
        this.main.getServer().getScheduler().cancelTask(i);
    }

    public boolean cancelTask(UUID uuid) {
        BukkitTask task = taskMap.remove(uuid);
        if (task != null) {
            task.cancel();
            return true;
        } else if (Config.DEBUG) {
            this.main.logMessage("No task can be canceled for " + uuid + '!');
        }
        return false;
    }

    public void createTask(UUID uuid) {
        if (Config.DEBUG) {
            this.main.logMessage("Scheduling daily save task for user " + uuid + '.');
        }
        DailySaveTask saveTask = new DailySaveTask();
        BukkitTask task = this.main.runTaskTimerAsynchronously(saveTask, 6000);
        saveTask.setUuid(uuid);
        saveTask.setUserManager(this);
        saveTask.setTaskId(task.getTaskId());
        BukkitTask old = this.taskMap.put(uuid, task);
        if (old != null) {
            if (Config.DEBUG) {
                this.main.logMessage("Already scheduled task for user " + uuid + '!');
            }
            old.cancel();
        }
    }

    public void setItemUtil(ItemUtil itemUtil) {
        this.itemUtil = itemUtil;
    }

    public void setExpUtil(ExpUtil expUtil) {
        this.expUtil = expUtil;
    }

    public void setMain(PluginMain main) {
        this.main = main;
    }

    public PluginMain getMain() {
        return main;
    }

}
