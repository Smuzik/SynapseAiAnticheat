package net.synapselabs.anticheat.data;

import net.synapselabs.anticheat.AiAnticheatPlugin;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                for (PlayerProfile p : profiles.values()) {
                    writer.write(PlayerRecordCodec.encode(p));
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player database: " + e.getMessage());
        }
    }

    private void loadDatabase() {
        File file = new File(plugin.getDataFolder(), "player_database.txt");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                PlayerProfile p = PlayerRecordCodec.decode(line);
                if (p == null) continue;
                profiles.put(p.getUuid(), p);
                indexIp(p.getLastIp(), p.getUuid());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load player database: " + e.getMessage());
        }
    }
}
