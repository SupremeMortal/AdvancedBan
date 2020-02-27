package me.leoko.advancedban.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.AdvancedBanPlayer;
import me.leoko.advancedban.configuration.Configuration;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UUIDManager {

    @Getter
    private static final UUIDManager instance = new UUIDManager();

    private FetcherMode mode;
    private final Map<String, UUID> activeUUIDs = new HashMap<>();

    public void onEnable() {
        if (AdvancedBan.get().getConfiguration().getUuidFetcher().isDynamic()) {
            if (!AdvancedBan.get().isOnlineMode()) {
                mode = FetcherMode.INTERNAL;
            } else {
                mode = AdvancedBan.get().getMode();
            }
        }else{
            if (!AdvancedBan.get().getConfiguration().getUuidFetcher().isEnabled()) {
                mode = FetcherMode.DISABLED;
            } else if (!AdvancedBan.get().getConfiguration().getUuidFetcher().isIntern()) {
                mode = FetcherMode.INTERNAL;
            }else{
                mode = FetcherMode.RESTFUL;
            }
        }
    }

    public Optional<UUID> getInitialUuid(String name) {
        name = name.toLowerCase();
        Optional<UUID> uuid = Optional.empty();

        if (mode == FetcherMode.DISABLED) {
            return uuid;
        }

        if (mode == FetcherMode.INTERNAL || mode == FetcherMode.MIXED) {
            uuid = AdvancedBan.get().getInternalUUID(name);
        }

        if (!uuid.isPresent() && AdvancedBan.get().isMojangAuthed()) {
            final Configuration.UUIDApi restApi = AdvancedBan.get().getConfiguration().getUuidFetcher().getRestApi();
            String url = restApi.getUrl();
            String key = restApi.getKey();
            try {
                uuid = Optional.ofNullable(askAPI(url, name, key));
            } catch (Exception e) {
                AdvancedBanLogger.getInstance().warn("Failed to retrieve UUID of " + name + " using REST-API");
                AdvancedBanLogger.getInstance().logException(e);
            }
        }

        if (!uuid.isPresent() && AdvancedBan.get().isMojangAuthed()) {
            AdvancedBanLogger.getInstance().debug("Trying to fetch UUID form BackUp-API...");
            String url = AdvancedBan.get().getConfiguration().getUuidFetcher().getBackupApi().getUrl();
            String key = AdvancedBan.get().getConfiguration().getUuidFetcher().getBackupApi().getKey();
            try {
                uuid = Optional.ofNullable(askAPI(url, name, key));
            } catch (Exception e) {
                AdvancedBanLogger.getInstance().severe("Failed to retrieve UUID of " + name + " using BACKUP REST-API");
                AdvancedBanLogger.getInstance().logException(e);
            }
        }
        return uuid;
    }

    public Optional<UUID> getUuid(String name) {
        if (activeUUIDs.containsKey(name)) {
            return Optional.ofNullable(activeUUIDs.get(name));
        }
        return getInitialUuid(name);
    }

    @SuppressWarnings("resource")
    public Optional<String> getNameFromUuid(UUID uuid, boolean forceInitial) {
        if (mode == FetcherMode.DISABLED) {
            return Optional.empty();
        }

        if (mode == FetcherMode.INTERNAL || mode == FetcherMode.MIXED) {
            Optional<String> name = AdvancedBan.get().getPlayer(uuid).map(AdvancedBanPlayer::getName);
            if (name.isPresent()) {
                return name;
            }
        }

        if (!forceInitial) {
            for (Entry<String, UUID> rs : activeUUIDs.entrySet()) {
                if (rs.getValue().equals(uuid)) {
                    return Optional.of(rs.getKey());
                }
            }
        }

        try {
            String s = new Scanner(new URL("https://api.mojang.com/user/profiles/" + uuid + "/names").openStream(), "UTF-8").useDelimiter("\\A").next();
            s = s.substring(s.lastIndexOf('{'), s.lastIndexOf('}') + 1);
            return Optional.ofNullable(AdvancedBan.JSON_MAPPER.readTree(s).get("name").textValue());
        } catch (Exception exc) {
            return Optional.empty();
        }
    }

    private UUID askAPI(String url, String name, String key) throws Exception {
        HttpURLConnection request = (HttpURLConnection) new URL(url.replaceAll("%NAME%", name).replaceAll("%TIMESTAMP%", new Date().getTime() + "")).openConnection();
        request.connect();

        String uuidString = AdvancedBan.JSON_MAPPER.readTree(new InputStreamReader(request.getInputStream())).get(key).textValue();

        if (uuidString == null) {
            throw new NoSuchFieldException(key + " does not exist");
        }

        UUID uuid = UUID.fromString(uuidString);
        activeUUIDs.put(name, uuid);

        return uuid;
    }

    public FetcherMode getMode() {
        return mode;
    }

    public enum FetcherMode{
        DISABLED, INTERNAL, MIXED, RESTFUL;
    }
}