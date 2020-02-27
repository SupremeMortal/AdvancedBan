package me.leoko.advancedban.punishment;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.leoko.advancedban.AdvancedBan;
import me.leoko.advancedban.AdvancedBanLogger;
import me.leoko.advancedban.AdvancedBanPlayer;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.MessageManager;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.utils.SQLQuery;

import javax.annotation.Nonnull;
import java.net.InetAddress;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PunishmentManager {

    @Getter
    private static final PunishmentManager instance = new PunishmentManager();

    private final AdvancedBanLogger logger = AdvancedBanLogger.getInstance();
    private final Set<Punishment> punishments = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Punishment> history = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Object> cached = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public void onEnable() {
        DatabaseManager.getInstance().executeStatement(SQLQuery.DELETE_OLD_PUNISHMENTS, TimeManager.getTime());

        AdvancedBan.get().getOnlinePlayers()
                .forEach(player -> load(player.getUniqueId(), player.getName(), player.getAddress().getAddress()));
    }

    private static String[] getDurationParameter(String... parameter) {
        int length = parameter.length;
        String[] newParameter = new String[length * 2];
        for (int i = 0; i < length; i += 2) {
            String name = parameter[i];
            String count = parameter[i + 1];

            newParameter[i] = name;
            newParameter[i + 1] = count;
            newParameter[length + i] = name + name;
            newParameter[length + i + 1] = (count.length() <= 1 ? "0" : "") + count;
        }

        return newParameter;
    }

    public InterimData load(@Nonnull UUID uuid, @Nonnull String name, @Nonnull InetAddress address) {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Set<Punishment> punishments = new HashSet<>();
        Set<Punishment> history = new HashSet<>();
        try {
            ResultSet rs = DatabaseManager.getInstance().executeResultStatement(SQLQuery.SELECT_USER_PUNISHMENTS_WITH_IP, uuid, address.getHostAddress());
            while (rs.next()) {
                punishments.add(getPunishmentFromResultSet(rs));
            }
            rs.close();

            rs = DatabaseManager.getInstance().executeResultStatement(SQLQuery.SELECT_USER_PUNISHMENTS_HISTORY_WITH_IP, uuid, address.getHostAddress());
            while (rs.next()) {
                history.add(getPunishmentFromResultSet(rs));
            }
            rs.close();
        } catch (SQLException ex) {
            logger.warn("An error has ocurred loading the punishments from the database.");
            logger.logException(ex);
        }
        return new InterimData(uuid, name, address, punishments, history);
    }

    public Optional<Punishment> getBan(@Nonnull InterimData data) {
        Objects.requireNonNull(data, "data");
        for (Punishment pt : data.getPunishments()) {
            if (pt.getType().getBasic() == PunishmentType.BAN && !pt.isExpired()) {
                return Optional.of(pt);
            }
        }
        return Optional.empty();
    }

    public void discard(AdvancedBanPlayer player) {
        cached.remove(player.getName());
        cached.remove(player.getUniqueId());
        cached.remove(player.getAddress());

        Predicate<Punishment> remove = pun -> pun.getIdentifier().equals(player.getUniqueId()) ||
                pun.getIdentifier().equals(player.getAddress().getAddress());

        punishments.removeIf(remove);
        history.removeIf(remove);
    }

    public void acceptData(@Nonnull InterimData data) {
        Objects.requireNonNull(data, "data");
        getLoadedPunishments(false).addAll(data.getPunishments());
        getLoadedHistory().addAll(data.getHistory());
        addCached(data.getName());
        addCached(data.getAddress());
        addCached(data.getUuid());
    }

    public List<Punishment> getPunishments(SQLQuery sqlQuery, Object... parameters) {
        List<Punishment> ptList = new ArrayList<>();

        ResultSet rs = DatabaseManager.getInstance().executeResultStatement(sqlQuery, parameters);
        try {
            while (rs.next()) {
                Punishment punishment = getPunishmentFromResultSet(rs);
                ptList.add(punishment);
            }
            rs.close();
        } catch (SQLException ex) {
            logger.info("An error has occurred executing a query in the database.");
            logger.debug("Query: \n" + sqlQuery);
            logger.logException(ex);
        }
        return ptList;
    }

    public List<Punishment> getPunishments(Object identifier, PunishmentType type, boolean current) {
        List<Punishment> punishments = new ArrayList<>();

        if (isCached(identifier)) {
            for (Iterator<Punishment> iterator = (current ? this.punishments : history).iterator(); iterator.hasNext(); ) {
                Punishment punishment = iterator.next();
                if ((type == null || type == punishment.getType().getBasic()) && punishment.getIdentifier().equals(identifier)) {
                    if (!current || !punishment.isExpired()) {
                        punishments.add(punishment);
                    } else {
                        deletePunishment(punishment, true);
                        iterator.remove();
                    }
                }
            }
        } else {
            try (ResultSet rs = DatabaseManager.getInstance().
                    executeResultStatement(current ? SQLQuery.SELECT_USER_PUNISHMENTS : SQLQuery.SELECT_USER_PUNISHMENTS_HISTORY,
                            identifier.toString())) {
                while (rs.next()) {
                    Punishment punishment = getPunishmentFromResultSet(rs);
                    if ((type == null || type == punishment.getType().getBasic()) && (!current || !punishment.isExpired())) {
                        punishments.add(punishment);
                    }
                }
            } catch (SQLException ex) {
                logger.info("An error has occurred getting the punishments for " + identifier);
                logger.logException(ex);
            }
        }
        return punishments;
    }

    public Optional<Punishment> getWarn(int id) {
        Optional<Punishment> punishment = getPunishment(id);
        return punishment.isPresent() && punishment.get().getType().getBasic() == PunishmentType.WARNING ? punishment : Optional.empty();
    }

    public List<Punishment> getWarns(Object target) {
        return getPunishments(target, PunishmentType.WARNING, true);
    }

    public Optional<Punishment> getPunishment(int id) {
        ResultSet rs = DatabaseManager.getInstance().executeResultStatement(SQLQuery.SELECT_PUNISHMENT_BY_ID, id);
        Punishment pt = null;
        try {
            if (rs.next()) {
                pt = getPunishmentFromResultSet(rs);
            }
            rs.close();
        } catch (SQLException ex) {
            logger.info("An error has ocurred getting a punishment by his id.");
            logger.debug("Punishment id: '" + id + "'");
            logger.logException(ex);
        }
        return pt == null || pt.isExpired() ? Optional.empty() : Optional.of(pt);
    }

    public Optional<Punishment> getPunishment(Object object, PunishmentType type) {
        return getPunishment(object, type, true);
    }

    public Optional<Punishment> getPunishment(Object object, PunishmentType type, boolean current) {
        List<Punishment> punishments = getPunishments(object, type, current);
        return punishments.isEmpty() ? Optional.empty() : Optional.ofNullable(punishments.get(0));
    }

    public boolean isMuted(Object object) {
        return getPunishment(object, PunishmentType.MUTE, true).isPresent();
    }

    public boolean isCached(Object name) {
        return cached.contains(name);
    }

    public void addCached(Object object) {
        cached.add(object);
    }

    public int getCalculationLevel(Object identifier, String layout) {
        if (isCached(identifier)) {
            return (int) history.stream().filter(pt -> pt.getIdentifier().equals(identifier) && layout.equalsIgnoreCase(pt.getCalculation())).count();
        } else {
            ResultSet resultSet = DatabaseManager.getInstance().executeResultStatement(SQLQuery.SELECT_USER_PUNISHMENTS_HISTORY_BY_CALCULATION, identifier.toString(), layout);
            int i = 0;
            try {
                while (resultSet.next()) {
                    i++;
                }
                resultSet.close();
            } catch (SQLException ex) {
                logger.warn("An error has occurred getting the level for the layout '" + layout + "' for '" + identifier + "'");
                logger.logException(ex);
            }
            return i;
        }
    }

    public int getCurrentWarns(Object object) {
        return getWarns(object).size();
    }

    public boolean isBanned(Object object) {
        return getPunishment(object, PunishmentType.BAN, true).isPresent();
    }

    public Set<Punishment> getLoadedPunishments(boolean checkExpired) {
        if (checkExpired) {
            List<Punishment> toDelete = new ArrayList<>();
            for (Punishment pu : punishments) {
                if (pu.isExpired()) {
                    toDelete.add(pu);
                }
            }
            for (Punishment pu : toDelete) {
                deletePunishment(pu, true);
            }
        }
        return punishments;
    }

    public Punishment getPunishmentFromResultSet(ResultSet rs) throws SQLException {
        Punishment punishment = new Punishment(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("name"),
                rs.getString("operator"),
                rs.getString("calculation"),
                rs.getLong("start"),
                rs.getLong("end"),
                PunishmentType.valueOf(rs.getString("punishmentType"))
        );
        punishment.setReason(rs.getString("reason"));
        punishment.setId(rs.getInt("id"));

        return punishment;
    }

    public Set<Punishment> getLoadedHistory() {
        return history;
    }

    public long getCalculation(String layout, String name, String uuid) {
        long end = TimeManager.getTime();

        int i = getCalculationLevel(name, uuid);

        List<String> timeLayout = MessageManager.getLayout("Time." + layout);
        String time = timeLayout.get(timeLayout.size() <= i ? timeLayout.size() - 1 : i);
        long toAdd = TimeManager.toMilliSec(time.toLowerCase());
        end += toAdd;

        return end;
    }

    public void updatePunishment(@Nonnull Punishment punishment) {
        Objects.requireNonNull(punishment, "punishment");
        if (!punishment.getId().isPresent()) throw new IllegalArgumentException("Punishment is not registered");

        DatabaseManager.getInstance().executeStatement(SQLQuery.UPDATE_PUNISHMENT_REASON,
                punishment.getReason().orElse(null), punishment.getId().getAsInt());
    }

    public void addPunishment(@Nonnull Punishment punishment) {
        addPunishment(punishment, false);
    }

    public void addPunishment(@Nonnull Punishment punishment, boolean silent) {
        Objects.requireNonNull(punishment, "punishment");
        if (punishment.getId().isPresent()) {
            throw new IllegalArgumentException("Punishment has already been added");
        }

        DatabaseManager.getInstance().executeStatement(
                SQLQuery.INSERT_PUNISHMENT_HISTORY,
                punishment.getName(),
                punishment.getIdentifier().toString(),
                punishment.getReason().orElse(null),
                punishment.getOperator(),
                punishment.getType().name(),
                punishment.getStart(),
                punishment.getEnd(),
                punishment.getCalculation()
        );

        if (punishment.getType() != PunishmentType.KICK) {
            ResultSet rs;
            try {
                DatabaseManager.getInstance().executeStatement(
                        SQLQuery.INSERT_PUNISHMENT,
                        punishment.getName(),
                        punishment.getIdentifier().toString(),
                        punishment.getReason().orElse(null),
                        punishment.getOperator(),
                        punishment.getType().name(),
                        punishment.getStart(),
                        punishment.getEnd(),
                        punishment.getCalculation()
                );
                rs = DatabaseManager.getInstance().executeResultStatement(SQLQuery.SELECT_EXACT_PUNISHMENT,
                        punishment.getIdentifier().toString(), punishment.getStart());
                if (rs.next()) {
                    punishment.setId(rs.getInt("id"));
                } else {
                    logger.warn("Not able to update ID of punishment! Please restart the server to resolve this issue!\n" + toString());
                }
                rs.close();
            } catch (SQLException ex) {
                logger.logException(ex);
            }
        }

        final int cWarnings = punishment.getType().getBasic() == PunishmentType.WARNING ? (getCurrentWarns(punishment.getIdentifier()) + 1) : 0;

        if (punishment.getType().getBasic() == PunishmentType.WARNING) {
            Optional<String> command = Optional.empty();
            for (int i = 1; i <= cWarnings; i++) {
                String action = AdvancedBan.get().getConfiguration().getWarnActions().get(i);
                if (action != null) {
                    command = Optional.of(action);
                }
            }
            command.ifPresent(cmd -> {
                final String finalCmd = cmd.replaceAll("%PLAYER%", punishment.getName())
                        .replaceAll("%COUNT%", cWarnings + "")
                        .replaceAll("%REASON%", punishment.getReason().orElse(AdvancedBan.get().getConfiguration().getDefaultReason()));
                AdvancedBan.get().runSyncTask(() -> {
                    AdvancedBan.get().executeCommand(finalCmd);
                    logger.info("Executed command: " + finalCmd);
                });
            });
        }

        if (!silent) {
            announce(punishment, cWarnings);
        }

        Optional<AdvancedBanPlayer> player = AdvancedBan.get().getPlayer(punishment.getIdentifier().toString());

        if (player.isPresent()) {
            if (punishment.getType().getBasic() == PunishmentType.BAN || punishment.getType() == PunishmentType.KICK) {
                AdvancedBan.get().runSyncTask(() -> player.get().kick(getLayoutBSN(punishment)));
            } else {
                for (String str : getLayout(punishment)) {
                    player.get().sendMessage(str);
                }
                getLoadedPunishments(false).add(punishment);
            }
        }

        getLoadedHistory().add(punishment);

        AdvancedBan.get().callPunishmentEvent(punishment);
    }

    public void deletePunishment(@Nonnull Punishment punishment, @Nonnull String operator) {
        String prefix = MessageManager.getPrefix().map(str -> str + " ").orElse("");
        String message = prefix +MessageManager.getMessage("Un" + punishment.getType().getBasic().getConfSection("Notification"),
                "OPERATOR", operator, "NAME", punishment.getName());

        AdvancedBan.get().notify("ab.undoNotify." + punishment.getType().getBasic().getName(), Collections.singletonList(message));

        AdvancedBanLogger.getInstance().debug(operator + " is deleting a punishment");

        deletePunishment(punishment);
    }

    public void deletePunishment(@Nonnull Punishment punishment) {
        deletePunishment(punishment, false);
    }

    public void deletePunishment(@Nonnull Punishment punishment, boolean massClear) {
        Objects.requireNonNull(punishment, "punishment");
        if (!punishment.getId().isPresent()) {
            throw new IllegalArgumentException("Punishment has not been added");
        }

        DatabaseManager.getInstance().executeStatement(SQLQuery.DELETE_PUNISHMENT, punishment.getId().getAsInt());

        getLoadedPunishments(false).remove(punishment);

        logger.debug("Deleted punishment " + punishment.getId().getAsInt() + " from " +
                punishment.getName() + " punishment reason: " +
                punishment.getReason().orElse(AdvancedBan.get().getConfiguration().getDefaultReason()));
        AdvancedBan.get().callRevokePunishmentEvent(punishment, massClear);
    }

    public String getDuration(@Nonnull Punishment punishment, boolean fromStart) {
        Objects.requireNonNull(punishment, "punishment");
        String duration = "permanent";
        if (punishment.getType().isTemp()) {
            long diff = ceilDiv((punishment.getEnd() - (fromStart ? punishment.getStart() : TimeManager.getTime())) , 1000);
            if (diff > 60 * 60 * 24) {
                duration = MessageManager.getMessage("General.TimeLayoutD", getDurationParameter("D", diff / 60 / 60 / 24 + "", "H", diff / 60 / 60 % 24 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + ""));
            } else if (diff > 60 * 60) {
                duration = MessageManager.getMessage("General.TimeLayoutH", getDurationParameter("H", diff / 60 / 60 + "", "M", diff / 60 % 60 + "", "S", diff % 60 + ""));
            } else if (diff > 60) {
                duration = MessageManager.getMessage("General.TimeLayoutM", getDurationParameter("M", diff / 60 + "", "S", diff % 60 + ""));
            } else {
                duration = MessageManager.getMessage("General.TimeLayoutS", getDurationParameter("S", diff + ""));
            }
        }
        return duration;
    }

    long ceilDiv(long x, long y) {
        return -Math.floorDiv(-x, y);
    }

    private void announce(Punishment punishment, int cWarnings) {
        List<String> notification = MessageManager.getMessageList(punishment.getType().getConfSection() + ".Notification",
                "OPERATOR", punishment.getOperator(),
                "PREFIX", AdvancedBan.get().getConfiguration().isPrefixDisabled() ? "" : MessageManager.getMessage("General.Prefix"),
                "DURATION", getDuration(punishment, true),
                "REASON", MessageManager.getReasonOrDefault(punishment.getReason()),
                "NAME", punishment.getName(),
                "ID", String.valueOf(punishment.getId().orElse(-1)),
                "HEXID", Integer.toHexString(punishment.getId().orElse(-1)).toUpperCase(),
                "DATE", TimeManager.getDate(punishment.getStart()),
                "COUNT", cWarnings + "");

        AdvancedBan.get().notify("ab.notify." + punishment.getType().getName() + "", notification);
    }

    public List<String> getLayout(@Nonnull Punishment punishment) {
        Objects.requireNonNull(punishment, "punishment");

        String operator = punishment.getOperator();
        String prefix = MessageManager.getPrefix().orElse("");
        String duration = getDuration(punishment, false);
        String hexId = Integer.toHexString(punishment.getId().orElse(-1)).toUpperCase();
        String id = Integer.toString(punishment.getId().orElse(-1));
        String date = TimeManager.getDate(punishment.getStart());
        String count = punishment.getType().getBasic() == PunishmentType.WARNING ?
                (getCurrentWarns(punishment.getIdentifier()) + 1) + "" : "0";

        if (punishment.getReason().isPresent() &&
                (punishment.getReason().get().startsWith("@") || punishment.getReason().get().startsWith("~"))) {
            return MessageManager.getLayout(
                    "Message." + punishment.getReason().get().split(" ")[0].substring(1),
                    "OPERATOR", operator,
                    "PREFIX", prefix,
                    "DURATION", duration,
                    "REASON", punishment.getReason().get().split(" ").length < 2 ? "" :
                            punishment.getReason().get().substring(punishment.getReason().get().split(" ")[0].length() + 1),
                    "HEXID", hexId,
                    "ID", id,
                    "DATE", date,
                    "COUNT", count
            );
        } else {
            return MessageManager.getMessageList(
                    punishment.getType().getConfSection() + ".Layout",
                    "OPERATOR", operator,
                    "PREFIX", prefix,
                    "DURATION", duration,
                    "REASON", MessageManager.getReasonOrDefault(punishment.getReason()),
                    "HEXID", hexId,
                    "ID", id,
                    "DATE", date,
                    "COUNT", count
            );
        }
    }

    public String getLayoutBSN(@Nonnull Punishment punishment) {
        StringBuilder msg = new StringBuilder();
        for (String str : getLayout(punishment)) {
            msg.append("\n").append(str);
        }
        return msg.substring(1);
    }
}