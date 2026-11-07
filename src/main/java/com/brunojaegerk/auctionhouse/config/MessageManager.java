package com.brunojaegerk.auctionhouse.config;

import cn.nukkit.utils.Config;
import com.brunojaegerk.auctionhouse.Main;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageManager {

    private final Main plugin;

    private Config chatMessages;
    private Config menuMessages;

    public MessageManager(Main plugin) {
        this.plugin = plugin;

        plugin.saveResource("messages.yml", false);
        plugin.saveResource("menus.yml", false);

        reload();
    }

    public void reload() {
        File dataFolder = plugin.getDataFolder();

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException(
                    "No se pudo crear la carpeta de datos."
            );
        }

        chatMessages = new Config(
                new File(dataFolder, "messages.yml"),
                Config.YAML
        );

        menuMessages = new Config(
                new File(dataFolder, "menus.yml"),
                Config.YAML
        );
    }

    public String getChat(String path) {
        return getChat(path, Collections.emptyMap());
    }

    public String getChat(
            String path,
            Map<String, ?> placeholders
    ) {
        String message = chatMessages.getString(
                path,
                "§cFalta el mensaje: " + path
        );

        return applyPlaceholders(
                colorize(message),
                placeholders
        );
    }

    public String getMenu(String path) {
        return getMenu(path, Collections.emptyMap());
    }

    public String getMenu(
            String path,
            Map<String, ?> placeholders
    ) {
        String message = menuMessages.getString(
                path,
                "§cFalta el texto de interfaz: " + path
        );

        return applyPlaceholders(
                colorize(message),
                placeholders
        );
    }

    public List<String> getMenuList(String path) {
        return getMenuList(path, Collections.emptyMap());
    }

    public List<String> getMenuList(
            String path,
            Map<String, ?> placeholders
    ) {
        List<String> configured =
                menuMessages.getStringList(path);

        List<String> result =
                new ArrayList<>(configured.size());

        for (String line : configured) {
            result.add(
                    applyPlaceholders(
                            colorize(line),
                            placeholders
                    )
            );
        }

        return result;
    }

    private String applyPlaceholders(
            String message,
            Map<String, ?> placeholders
    ) {
        if (message == null || placeholders == null
                || placeholders.isEmpty()) {
            return message;
        }

        String result = message;

        for (Map.Entry<String, ?> entry
                : placeholders.entrySet()) {

            result = result.replace(
                    "{" + entry.getKey() + "}",
                    String.valueOf(entry.getValue())
            );
        }

        return result;
    }

    private String colorize(String text) {
        return text == null
                ? ""
                : text.replace('&', '§');
    }
}

