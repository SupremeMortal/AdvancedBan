package me.leoko.advancedban;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.AccessLevel;
import lombok.Getter;
import me.leoko.advancedban.configuration.Configuration;
import me.leoko.advancedban.configuration.Layouts;
import me.leoko.advancedban.configuration.Messages;
import me.leoko.advancedban.configuration.MySQLConfiguration;
import me.leoko.advancedban.manager.*;
import me.leoko.advancedban.punishment.InterimData;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;

/**
 * @author SupremeMortal
 */
@Getter
public abstract class AdvancedBan {
    @Getter(value = AccessLevel.NONE)
    public static final YAMLMapper YAML_MAPPER = (YAMLMapper) new YAMLMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    @Getter(value = AccessLevel.NONE)
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    @Getter(value = AccessLevel.NONE)
    private static AdvancedBan instance;

    private final Map<Object, AdvancedBanPlayer> players = Collections.synchronizedMap(new HashMap<>());
    @Getter(value = AccessLevel.NONE)
    private final Map<Object, InetAddress> addresses = Collections.synchronizedMap(new HashMap<>());
    private final UUIDManager.FetcherMode mode;
    private final boolean mojangAuthed;
    private final Set<String> commands = new HashSet<>();
    private Configuration configuration;
    private Layouts layouts;
    private Messages messages;
    @Getter(value = AccessLevel.NONE)
    private MySQLConfiguration mySQLConfiguration = null;

    protected AdvancedBan(UUIDManager.FetcherMode mode, boolean mojangAuthed) {
        if (instance != null) {
            throw new IllegalStateException("AdvancedBan has already been initialized");
        }

        instance = this;
        this.mode = mode;
        this.mojangAuthed = mojangAuthed;
    }

    public static AdvancedBan get() {
        return instance;
    }

    public final void onEnable() {
        try {
            loadFiles();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load configuration files", e);
        }

        AdvancedBanLogger.getInstance().onEnable();
        DatabaseManager.getInstance().onEnable();
        boolean changes = UpdateManager.migrateFiles();
        UUIDManager.getInstance().onEnable();
        PunishmentManager.getInstance().onEnable();
        CommandManager.getInstance().onEnable();

        if(changes){
            try {
                loadFiles();
            } catch (IOException e) {
                throw new IllegalStateException("Unable to load configuration files", e);
            }
        }


        String upt = "You have the newest version";
        String currentVersion = requestCurrentVersion();
        if (currentVersion == null) {
            upt = "Failed to check for updates :(";
        } else if (!getVersion().startsWith(currentVersion)) {
            upt = "There is a new version available! [" + currentVersion + "]";
        }

        if (getConfiguration().isDetailedEnableMessage()) {
            logToConsoleSender("\n \n§8[]=====[§7Enabling AdvancedBan§8]=====[]"
                    + "\n§8| §cInformation:"
                    + "\n§8|   §cName: §7AdvancedBan"
                    + "\n§8|   §cDeveloper: §7Leoko"
                    + "\n§8|   §cVersion: §7" + getVersion()
                    + "\n§8|   §cStorage: §7" + (DatabaseManager.getInstance().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)")
                    + "\n§8| §cSupport:"
                    + "\n§8|   §cGithub: §7https://github.com/DevLeoko/AdvancedBan/issues"
                    + "\n§8|   §cDiscord: §7https://discord.gg/ycDG6rS"
                    + "\n§8| §cUpdate:"
                    + "\n§8|   §7" + upt
                    + "\n§8[]================================[]§r\n ");
        } else {
            logToConsoleSender("§cEnabling AdvancedBan on Version §7" + getVersion());
            logToConsoleSender("§7§o"+upt);
        }
    }

    public final void onDisable() {
        DatabaseManager.getInstance().onDisable();

        if (getConfiguration().isDetailedDisableMessage()) {
            logToConsoleSender("\n \n§8[]=====[§7Disabling  AdvancedBan§8]=====[]"
                    + "\n§8| §cInformation:"
                    + "\n§8|   §cName: §7AdvancedBan"
                    + "\n§8|   §cDeveloper: §7Leoko"
                    + "\n§8|   §cVersion: §7" + getVersion()
                    + "\n§8|   §cStorage: §7" + (DatabaseManager.getInstance().isUseMySQL() ? "MySQL (external)" : "HSQLDB (local)")
                    + "\n§8| §cSupport:"
                    + "\n§8|   §cGithub: §7https://github.com/DevLeoko/AdvancedBan/issues"
                    + "\n§8|   §cDiscord: §7https://discord.gg/ycDG6rS"
                    + "\n§8[]================================[]§r\n ");
        } else {
            logToConsoleSender("§cDisabling AdvancedBan on Version §7" + getVersion());
        }
    }

    private static String requestCurrentVersion(){
        String response = null;
        try {
            InputStream versionStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=8695").openStream();
            Scanner s = new Scanner(versionStream);
            if (s.hasNext())
                response = s.next();
            s.close();
            versionStream.close();

        } catch (IOException exc) {
            AdvancedBanLogger.getInstance().logException(exc);
        }
        return response;
    }

    public void logToConsoleSender(String message){
        AdvancedBanLogger.getInstance().info(message);
    }

    public final void loadFiles() throws IOException {
        Path dataPath = getDataFolderPath();
        Files.createDirectories(dataPath);
        Path configPath = checkExists("config.yml");
        configuration = Configuration.load(configPath);
        Path layoutsPath = checkExists("Layouts.yml");
        layouts = Layouts.load(layoutsPath);
        Path messagesPath = checkExists("Messages.yml");
        messages = Messages.load(messagesPath);
        if (configuration.isUsingMySQL()) {
            Path mysqlPath = checkExists("MySQL.yml");
            mySQLConfiguration = MySQLConfiguration.load(mysqlPath);
        }
    }

    private Path checkExists(String file) throws IOException {
        Path filePath = getDataFolderPath().resolve(file);
        InputStream resource = AdvancedBan.class.getClassLoader().getResourceAsStream(file);
        if (resource == null) {
            throw new IllegalStateException("Resource was not found in JAR");
        }
        if (Files.notExists(filePath) || !Files.isRegularFile(filePath)) {
            Files.deleteIfExists(filePath);
            Files.createFile(filePath);
            Files.copy(resource, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return filePath;
    }

    public Optional<MySQLConfiguration> getMySQLConfiguration() {
        return Optional.ofNullable(mySQLConfiguration);
    }

    public boolean isOnline(String name) {
        return getPlayer(name).isPresent();
    }

    public boolean isOnline(UUID uuid) {
        return getPlayer(uuid).isPresent();
    }

    public void notify(String permission, Collection<String> notifications) {
        for (AdvancedBanPlayer player : getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                notifications.forEach(player::sendMessage);
            }
        }
    }

    public Optional<String> onPreLogin(String name, UUID uuid, InetAddress address) {
        InterimData interimData = PunishmentManager.getInstance().load(uuid, name, address);

        Optional<Punishment> punishment = PunishmentManager.getInstance().getBan(interimData);

        if (!punishment.isPresent()) {
            PunishmentManager.getInstance().acceptData(interimData);
            addresses.put(name, address);
            addresses.put(uuid, address);
        }

        return punishment.map(pun -> PunishmentManager.getInstance().getLayoutBSN(pun));
    }

    public void onLogin(AdvancedBanPlayer player) {
        registerPlayer(player);
    }

    protected final void registerPlayer(AdvancedBanPlayer player) {
        players.put(player.getUniqueId(), player);
        players.put(player.getName().toLowerCase(), player);
        players.put(player.getAddress(), player);
    }

    public void onDisconnect(AdvancedBanPlayer player) {
        removePlayer(player);
        addresses.remove(player.getName());
        addresses.remove(player.getUniqueId());
    }

    protected void removePlayer(AdvancedBanPlayer player) {
        players.remove(player.getUniqueId());
        players.remove(player.getName());
        players.remove(player.getAddress());
        PunishmentManager.getInstance().discard(player);
    }

    public boolean onChat(AdvancedBanPlayer player, String message) {
        Optional<List<String>> layout = PunishmentManager.getInstance().getPunishment(player.getUniqueId(), PunishmentType.MUTE)
                .map(pun -> PunishmentManager.getInstance().getLayout(pun));
        if (layout.isPresent()) {
            layout.get().forEach(player::sendMessage);
            return true;
        }
        return false;
    }

    public boolean onCommand(AdvancedBanPlayer player, String command) {
        Optional<List<String>> layout = PunishmentManager.getInstance().getPunishment(player.getUniqueId(), PunishmentType.MUTE)
                .map(pun -> PunishmentManager.getInstance().getLayout(pun));
        if (layout.isPresent() && isMutedCommand(command)) {
            layout.get().forEach(player::sendMessage);
            return true;
        }
        return false;
    }

    public boolean isMutedCommand(String command) {
        command = command.split(" ")[0];

        for (String mutedCommand : configuration.getMuteCommands()) {
            if (mutedCommand.equalsIgnoreCase(command)) {
                return true;
            }
        }
        return false;
    }

    public Optional<AdvancedBanPlayer> getPlayer(UUID uuid) {
        return Optional.ofNullable(players.get(uuid));
    }

    public Optional<AdvancedBanPlayer> getPlayer(String val) {
        Optional<AdvancedBanPlayer> player = Optional.ofNullable(players.get(val.toLowerCase()));

        if (!player.isPresent()) {
            try {
                player = Optional.ofNullable(players.get(UUID.fromString(val)));
            } catch (Exception e) {
                // Ignore
            }
        }

        if (!player.isPresent()) {
            try {
                String[] addressPort = val.split(":");
                InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(addressPort[0]), Integer.parseInt(addressPort[1]));
                player = Optional.ofNullable(players.get(address));
            } catch (Exception e) {
                // Ignore
            }
        }
        return player;
    }

    public Optional<AdvancedBanPlayer> getPlayer(InetSocketAddress address) {
        return Optional.ofNullable(players.get(address));
    }

    public Collection<AdvancedBanPlayer> getOnlinePlayers() {
        return Collections.unmodifiableList(new ArrayList<>(new HashSet<>(players.values())));
    }

    public Optional<InetAddress> getAddress(Object value) {
        if (value instanceof InetAddress) return Optional.of((InetAddress) value);
        return Optional.ofNullable(addresses.get(value));
    }

    protected abstract void log(Level level, String msg);

    public abstract String getVersion();

    public abstract void registerCommand(String commandName);

    public abstract void executeCommand(String command);

    public abstract Path getDataFolderPath();

    public abstract void scheduleRepeatingAsyncTask(Runnable runnable, long delay, long period);

    public abstract void scheduleAsyncTask(Runnable runnable, long delay);

    public abstract void runAsyncTask(Runnable runnable);

    public abstract void runSyncTask(Runnable runnable);

    public abstract boolean isOnlineMode();

    public abstract void callPunishmentEvent(Punishment punishment);

    public abstract void callRevokePunishmentEvent(Punishment punishment, boolean massClear);

    public abstract Optional<UUID> getInternalUUID(String name);

    public abstract boolean isUnitTesting();
}
