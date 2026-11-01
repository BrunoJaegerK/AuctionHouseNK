package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import com.brunojaegerk.auctionhouse.Main;

import java.util.Map;

public class ListingLimitManager {

    private final Main plugin;

    public ListingLimitManager(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtiene el límite máximo de publicaciones del jugador.
     *
     * 0 significa publicaciones ilimitadas.
     */
    public int getMaxListings(Player player) {

        // Los administradores no tienen límite.
        if (player.hasPermission("auctionhouse.admin")) {
            return 0;
        }

        Map<String, Object> limits = plugin.getConfig()
                .getSection("auction.listing-limits");

        if (limits == null || limits.isEmpty()) {
            return plugin.getConfig().getInt(
                    "auction.default-max-listings",
                    5
            );
        }

        // Límite base para jugadores sin rango configurado.
        int maxListings = getLimit(
                limits.get("default"),
                plugin.getConfig().getInt(
                        "auction.default-max-listings",
                        5
                )
        );

        // Comprueba los permisos de cada rango configurado.
        for (Map.Entry<String, Object> entry : limits.entrySet()) {

            String group = entry.getKey();

            if (group.equalsIgnoreCase("default")) {
                continue;
            }

            String permission = "auctionhouse.limit." + group;

            if (!player.hasPermission(permission)) {
                continue;
            }

            int groupLimit = getLimit(entry.getValue(), maxListings);

            // Cero significa ilimitado.
            if (groupLimit == 0) {
                return 0;
            }

            // Si hereda varios rangos, utiliza el límite más alto.
            if (groupLimit > maxListings) {
                maxListings = groupLimit;
            }
        }

        return maxListings;
    }

    private int getLimit(Object value, int fallback) {

        if (value instanceof Number) {
            int limit = ((Number) value).intValue();
            return limit >= 0 ? limit : fallback;
        }

        if (value != null) {
            try {
                int limit = Integer.parseInt(value.toString());
                return limit >= 0 ? limit : fallback;
            } catch (NumberFormatException ignored) {
                // Utiliza el valor alternativo.
            }
        }

        return fallback;
    }
}

