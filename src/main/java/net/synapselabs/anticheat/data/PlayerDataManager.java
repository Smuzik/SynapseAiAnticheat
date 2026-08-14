package net.synapselabs.anticheat.data;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private final AiAnticheatPlugin plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> ipIndex = new ConcurrentHashMap<>();

    public PlayerDataManager(AiAnticheatPlugin plugin) {
        this.plugin = plugin;
        loadDatabase();
    }

    public PlayerProfile getOrCreate(Player player) {
        String ip = (player.getAddress() != null) ? player.getAddress().getAddress().getHostAddress() : "127.0.0.1";
        PlayerProfile profile = profiles.computeIfAbsent(player.getUniqueId(), k -> new PlayerProfile(player.getUniqueId(), player.getName(), ip));
        profile.setName(player.getName());
        profile.updateLastJoin(ip);
        indexIp(ip, player.getUniqueId());
        return profile;
    }

    public PlayerProfile getProfile(UUID uuid) {
        return profiles.get(uuid);
    }

    public PlayerProfile getProfileByName(String name) {
        for (PlayerProfile p : profiles.values()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private void indexIp(String ip, UUID uuid) {
        if (ip == null || ip.isBlank()) return;
        ipIndex.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    public List<PlayerProfile> getAccountsOnIp(String ip) {
        if (ip == null) return Collections.emptyList();
        Set<UUID> uuids = ipIndex.get(ip);
        if (uuids == null) return Collections.emptyList();

        List<PlayerProfile> list = new ArrayList<>();
        for (UUID u : uuids) {
            PlayerProfile p = profiles.get(u);
            if (p != null) list.add(p);
        }
        return list;
    }

    public boolean hasBannedAccountsOnIp(String ip) {
        for (PlayerProfile p : getAccountsOnIp(ip)) {
            if (p.isBanned()) return true;
            if (plugin.getPunishmentManager() != null && plugin.getPunishmentManager().isPlayerBanned(p.getName(), ip)) {
                return true;
            }
        }
        return false;
    }

    public int getAltCount(String ip) {
        Set<UUID> set = ipIndex.get(ip);
        return (set != null) ? set.size() : 1;
    }

    public Collection<PlayerProfile> getAllProfiles() {
        return profiles.values();
    }

    public void saveDatabase() {
        File file = new File(plugin.getDataFolder(), "player_database.txt");
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (PlayerProfile p : profiles.values()) {
                    writer.write(String.format("%s;%s;%d;%d;%s;%d;%d;%b;%s;%.2f\n",
                            p.getUuid(),
                            p.getName(),
                            p.getFirstJoin(),
                            p.getLastJoin(),
                            p.getLastIp(),
                            p.getAiFlagsCount(),
                            p.getGrimFlagsCount(),
                            p.isBanned(),
                            p.getLastFlagReason(),
                            p.getLastFlagConfidence()
                    ));
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player database: " + e.getMessage());
        }
    }

    private void loadDatabase() {
        File file = new File(plugin.getDataFolder(), "player_database.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(";");
                if (parts.length >= 8) {
                    UUID u = UUID.fromString(parts[0]);
                    String name = parts[1];
                    long first = Long.parseLong(parts[2]);
                    long last = Long.parseLong(parts[3]);
                    String ip = parts[4];
                    int aiFlags = Integer.parseInt(parts[5]);
                    int grimFlags = Integer.parseInt(parts[6]);
                    boolean banned = Boolean.parseBoolean(parts[7]);

                    PlayerProfile p = new PlayerProfile(u, name, ip);
                    p.setFirstJoin(first);
                    p.updateLastJoin(ip);
                    p.setBanned(banned);
                    if (parts.length >= 10) {
                        p.incrementAiFlags(parts[8], Float.parseFloat(parts[9]));
                    }
                    profiles.put(u, p);
                    indexIp(ip, u);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player database: " + e.getMessage());
        }
    }
}
