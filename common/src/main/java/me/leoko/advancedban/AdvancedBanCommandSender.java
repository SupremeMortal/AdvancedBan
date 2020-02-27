package me.leoko.advancedban;

import me.leoko.advancedban.manager.MessageManager;

public interface AdvancedBanCommandSender {
    String getName();

    void sendMessage(String message);

    default void sendCustomMessage(String path) {
        sendCustomMessage(path, true);
    }

    default void sendCustomMessage(String path, boolean prefix) {
        sendCustomMessage(path, prefix, new Object[0]);
    }

    default void sendCustomMessage(String path, boolean prefix, Object... parameters) {
        MessageManager.sendMessage(this, path, prefix, parameters);
    }

    boolean executeCommand(String command);

    boolean hasPermission(String permission);
}
