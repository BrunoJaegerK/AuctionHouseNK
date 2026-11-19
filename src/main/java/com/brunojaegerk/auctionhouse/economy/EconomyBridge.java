package com.brunojaegerk.auctionhouse.economy;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginBase;
import me.onebone.economyapi.EconomyAPI;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Puente de economía. EconomyAPI sigue siendo la moneda predeterminada;
 * EconomyExpandNK se consulta mediante su API pública para no acoplar
 * AuctionHouse a la implementación interna del plugin.
 */
public final class EconomyBridge {

    public static final String ECONOMY_API = "economyapi";

    private final PluginBase plugin;
    private final EconomyAPI economyAPI;

    public EconomyBridge(PluginBase plugin) {
        this.plugin = plugin;
        this.economyAPI = EconomyAPI.getInstance();
    }

    public String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isAvailable(String currencyId) {
        String id = normalizeId(currencyId);
        if (ECONOMY_API.equals(id)) {
            return economyAPI != null;
        }
        return getCurrency(id) != null;
    }

    public String getSymbol(String currencyId) {
        String id = normalizeId(currencyId);
        if (ECONOMY_API.equals(id)) {
            return "$";
        }
        Object currency = getCurrency(id);
        if (currency == null) {
            return "§c?";
        }
        Object symbol = invoke(currency, "getSymbol");
        return symbol == null ? "" : String.valueOf(symbol);
    }

    public List<String> getAvailableCurrencyIds() {
        List<String> ids = new ArrayList<>();
        if (economyAPI != null) {
            ids.add(ECONOMY_API);
        }
        Object secondary = getSecondaryEconomy();
        if (secondary == null) {
            return ids;
        }
        try {
            Class<?> api = Class.forName(
                    "com.bruno.economyexpand.api.SecondaryEconomy",
                    true,
                    secondary.getClass().getClassLoader()
            );
            Object value = api.getMethod("getCurrencies").invoke(secondary);
            if (value instanceof Collection<?> currencies) {
                for (Object currency : currencies) {
                    Object enabled = invoke(currency, "isEnabled");
                    Object id = invoke(currency, "getId");
                    if (Boolean.TRUE.equals(enabled) && id != null) {
                        ids.add(normalizeId(String.valueOf(id)));
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning(
                    "No se pudieron consultar las monedas de EconomyExpandNK: "
                            + exception.getMessage()
            );
        }
        return ids;
    }

    public boolean hasAccount(UUID player, String currencyId) {
        String id = normalizeId(currencyId);
        if (ECONOMY_API.equals(id)) {
            return economyAPI != null && economyAPI.hasAccount(player);
        }
        return getBalance(player, id) != null;
    }

    public boolean withdraw(UUID player, String currencyId, double amount) {
        String id = normalizeId(currencyId);
        if (!Double.isFinite(amount) || amount <= 0) {
            return false;
        }
        if (ECONOMY_API.equals(id)) {
            return economyAPI != null
                    && economyAPI.reduceMoney(player, amount) == EconomyAPI.RET_SUCCESS;
        }
        Object result = invokeSecondary(
                "withdraw",
                new Class<?>[]{UUID.class, String.class, BigDecimal.class},
                player, id, BigDecimal.valueOf(amount)
        );
        return Boolean.TRUE.equals(result);
    }

    public boolean deposit(UUID player, String currencyId, double amount) {
        String id = normalizeId(currencyId);
        if (!Double.isFinite(amount) || amount <= 0) {
            return false;
        }
        if (ECONOMY_API.equals(id)) {
            return economyAPI != null
                    && economyAPI.addMoney(player, amount) == EconomyAPI.RET_SUCCESS;
        }
        Object result = invokeSecondary(
                "deposit",
                new Class<?>[]{UUID.class, String.class, BigDecimal.class},
                player, id, BigDecimal.valueOf(amount)
        );
        return Boolean.TRUE.equals(result);
    }

    private BigDecimal getBalance(UUID player, String currencyId) {
        Object result = invokeSecondary(
                "getBalance",
                new Class<?>[]{UUID.class, String.class},
                player, currencyId
        );
        return result instanceof BigDecimal ? (BigDecimal) result : null;
    }

    private Object getCurrency(String id) {
        Object secondary = getSecondaryEconomy();
        if (secondary == null) {
            return null;
        }
        try {
            ClassLoader loader = secondary.getClass().getClassLoader();
            Class<?> api = Class.forName(
                    "com.bruno.economyexpand.api.SecondaryEconomy", true, loader
            );
            return api.getMethod("getCurrency", String.class).invoke(secondary, id);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }

    private Object getSecondaryEconomy() {
        Plugin found = plugin.getServer().getPluginManager()
                .getPlugin("EconomyExpandNK");
        if (found == null || !found.isEnabled()) {
            return null;
        }
        try {
            return found.getClass().getMethod("getEconomy").invoke(found);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning(
                    "No se pudo acceder a la API de EconomyExpandNK: "
                            + exception.getMessage()
            );
            return null;
        }
    }

    private Object invokeSecondary(
            String method,
            Class<?>[] parameterTypes,
            Object... arguments
    ) {
        Object secondary = getSecondaryEconomy();
        if (secondary == null) {
            return null;
        }
        try {
            Class<?> api = Class.forName(
                    "com.bruno.economyexpand.api.SecondaryEconomy",
                    true,
                    secondary.getClass().getClassLoader()
            );
            Method apiMethod = api.getMethod(method, parameterTypes);
            return apiMethod.invoke(secondary, arguments);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().error(
                    "Error al ejecutar " + method + " en EconomyExpandNK.",
                    exception
            );
            return null;
        }
    }

    private Object invoke(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Class<?> currencyApi = Class.forName(
                    "com.bruno.economyexpand.api.Currency",
                    true,
                    target.getClass().getClassLoader()
            );
            return currencyApi.getMethod(method).invoke(target);
        } catch (ReflectiveOperationException | LinkageError exception) {
            return null;
        }
    }
}
