package me.leoko.advancedban;

import me.leoko.advancedban.commands.Command;
import me.leoko.advancedban.manager.DatabaseManager;
import me.leoko.advancedban.manager.TimeManager;
import me.leoko.advancedban.punishment.InterimData;
import me.leoko.advancedban.punishment.Punishment;
import me.leoko.advancedban.punishment.PunishmentManager;
import me.leoko.advancedban.punishment.PunishmentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by Leo on 07.08.2017.
 */
public class PunishmentTest {

    @BeforeAll
    @ExtendWith(TempDirectory.class)
    public void onEnable(@TempDirectory.TempDir Path dataFolder) throws IOException {
        new TestAdvancedBan(dataFolder).onEnable();
    }

    @Test
    public void shouldCreatePunishmentForGivenUserWithGivenReason(){
        assertFalse(PunishmentManager.getInstance().isBanned("leoko"), "User should not be banned by default");
        Command.BAN.execute(new TestCommandSender("UnitTest"), new String[]{"Leoko", "Doing", "some", "unit-testing"});
        assertTrue(PunishmentManager.getInstance().isBanned("leoko"), "Punishment from above has failed");
        assertEquals(PunishmentManager.getInstance().getPunishment("leoko", PunishmentType.BAN).orElseThrow(() -> new AssertionError("Ban does not exist"))
                .getReason().orElseThrow(() -> new AssertionError("Reason does not exist")), "Doing some unit-testing", "Reason should match");
    }

    @Test
    public void shouldKeepPunishmentAfterRestart(){
        Punishment punishment = new Punishment("leoko", "leoko", "Testing", null, TimeManager.getTime(), -1, PunishmentType.MUTE);
        punishment.setReason("Persistence test");
        PunishmentManager.getInstance().addPunishment(punishment);
        int id = punishment.getId().getAsInt();
        DatabaseManager.getInstance().onEnable();
        DatabaseManager.getInstance().onDisable();
        Optional<Punishment> punishment1 = PunishmentManager.getInstance().getPunishment(id);
        assertTrue(punishment1.isPresent(), "Punishment should exist");
        assertEquals(punishment1.orElseThrow(IllegalStateException::new).getReason()
                .orElseThrow(() -> new AssertionError("Reason does not exist")), "Persistence test", "Reason should still match");
    }

    @Test
    public void shouldWorkWithCachedAndNotCachedPunishments() throws UnknownHostException {
        Punishment punishment = new Punishment("cache", "Cache Testing", "Cache Testing", null, TimeManager.getTime(), -1, PunishmentType.MUTE);
        PunishmentManager.getInstance().addPunishment(punishment);
        assertFalse(PunishmentManager.getInstance().getLoadedPunishments(false).contains(punishment), "Punishment should not be cached if user is not online");
        assertTrue(PunishmentManager.getInstance().isBanned("cache"), "Punishment should be active even if not in cache");
        InterimData data = PunishmentManager.getInstance().load(UUID.randomUUID(), "cache", InetAddress.getLocalHost());
        PunishmentManager.getInstance().acceptData(data);
        assertTrue(PunishmentManager.getInstance().getLoadedPunishments(false).stream().anyMatch(pt -> pt.getIdentifier().equals("cache")), "Punishment should be cached after user is loaded");
        assertTrue(PunishmentManager.getInstance().isBanned("cache"), "Punishment should be still active when in cache");
    }

    @AfterAll
    public void onDisable() {
        AdvancedBan.get().onDisable();
    }
}