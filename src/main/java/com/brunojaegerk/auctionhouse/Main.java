package com.brunojaegerk.auctionhouse;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;

import com.brunojaegerk.auctionhouse.auction.AuctionManager;
import com.brunojaegerk.auctionhouse.auction.AuctionMenu;
import com.brunojaegerk.auctionhouse.auction.AuctionService;
import com.brunojaegerk.auctionhouse.auction.ItemSerializer;
import com.brunojaegerk.auctionhouse.storage.DatabaseManager;
import com.brunojaegerk.auctionhouse.storage.PendingItemManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;

public class Main extends PluginBase {

    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private AuctionService auctionService;
    private PendingItemManager pendingItemManager;
    private AuctionMenu auctionMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);

        try {
            databaseManager.initialize();

            auctionManager = new AuctionManager(databaseManager);
            this.auctionMenu = new AuctionMenu(auctionManager);
            pendingItemManager = new PendingItemManager(databaseManager);

            auctionService = new AuctionService(
                    databaseManager,
                    auctionManager,
                    pendingItemManager
            );

        } catch (SQLException exception) {
            getLogger().error(
                    "No se pudo inicializar SQLite. AuctionHouse se desactivará.",
                    exception
            );

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info(
                "AuctionHouse 1.0.0 se ha habilitado correctamente."
        );
        getLogger().info("Base de datos SQLite conectada.");
        getLogger().info("Gestor de subastas inicializado.");
        getLogger().info("Gestor de objetos pendientes inicializado.");
        getLogger().info("Servicio de subastas inicializado.");
        getLogger().info("Economía: EconomyAPI.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("AuctionHouse se ha deshabilitado.");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public PendingItemManager getPendingItemManager() {
        return pendingItemManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (!command.getName().equalsIgnoreCase("ah")) {
            return false;
        }

        if (!sender.hasPermission("auctionhouse.use")) {
            sender.sendMessage(
                    "§cNo tienes permiso para usar este comando."
            );
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(
                    "§cEste comando solo puede utilizarse dentro del juego."
            );
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 1
                && args[0].equalsIgnoreCase("testitem")) {

            if (!sender.hasPermission("auctionhouse.admin")) {
                sender.sendMessage(
                        "§cNo tienes permiso para usar esta prueba."
                );
                return true;
            }

            testItemSerialization(player);
            return true;
        }

        if (args.length == 2
                && args[0].equalsIgnoreCase("sell")) {

            if (!player.hasPermission("auctionhouse.sell")) {
                player.sendMessage(
                        "§cNo tienes permiso para publicar objetos."
                );
                return true;
            }

            sellItem(player, args[1]);
            return true;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase("sell")) {
            player.sendMessage("§cUso correcto: /ah sell <precio>");
            return true;
        }

        auctionMenu.open(player);
        return true;
    }

    private void testItemSerialization(Player player) {
        Item original = player.getInventory().getItemInHand();

        if (original == null || original.isNull()) {
            player.sendMessage("§cDebes tener un objeto en la mano.");
            return;
        }

        try {
            byte[] serialized = ItemSerializer.serialize(original);
            Item restored = ItemSerializer.deserialize(serialized);

            boolean sameId = original.getId() == restored.getId();
            boolean sameDamage = original.getDamage() == restored.getDamage();
            boolean sameCount = original.getCount() == restored.getCount();

            boolean sameNbt = Arrays.equals(
                    original.getCompoundTag(),
                    restored.getCompoundTag()
            );

            if (sameId && sameDamage && sameCount && sameNbt) {
                player.sendMessage(
                        "§aPrueba correcta: el objeto se conserva."
                );
                player.sendMessage(
                        "§7Objeto: " + original.getName()
                );
                player.sendMessage(
                        "§7Cantidad: " + original.getCount()
                );
                player.sendMessage(
                        "§7Datos NBT: §aidénticos"
                );
            } else {
                player.sendMessage(
                        "§cLa prueba falló: los datos no coinciden."
                );
                getLogger().warning(
                        "La serialización no coincide para "
                                + player.getName()
                );
            }

        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage(
                    "§cError al probar la serialización. "
                            + "Revisa la consola."
            );

            getLogger().error(
                    "Error de serialización de objetos.",
                    exception
            );
        }
    }

    private void sellItem(Player player, String priceArgument) {
        final double price;

        try {
            price = Double.parseDouble(priceArgument);
        } catch (NumberFormatException exception) {
            player.sendMessage("§cEl precio debe ser un número válido.");
            return;
        }

        if (!Double.isFinite(price) || price <= 0) {
            player.sendMessage("§cEl precio debe ser positivo.");
            return;
        }

        double minPrice = getConfig().getDouble("auction.min-price", 1.0);
        double maxPrice = getConfig().getDouble(
                "auction.max-price",
                1000000000.0
        );

        if (price < minPrice || price > maxPrice) {
            player.sendMessage(
                    "§cEl precio debe estar entre "
                            + minPrice + " y " + maxPrice + "."
            );
            return;
        }

        int maxListings = getConfig().getInt(
                "auction.default-max-listings",
                5
        );

        if (maxListings <= 0) {
            player.sendMessage(
                    "§cLa publicación de objetos está deshabilitada temporalmente."
            );
            return;
        }

        try {
            int currentListings = auctionManager
                    .getActiveAuctionsBySeller(player.getUniqueId())
                    .size();

            if (currentListings >= maxListings) {
                player.sendMessage(
                        "§cHas alcanzado tu límite de "
                                + maxListings + " publicaciones activas."
                );
                return;
            }

            long durationHours = getConfig().getLong(
                    "auction.listing-duration-hours",
                    24
            );

            if (durationHours <= 0
                    || durationHours > Long.MAX_VALUE / 3600000L) {
                player.sendMessage(
                        "§cLa duración de las publicaciones está mal configurada."
                );
                return;
            }

            long durationMillis = durationHours * 3600000L;

            long listingId = auctionService.prepareListing(
                    player,
                    price,
                    durationMillis
            );

            player.sendMessage("§a¡Publicación creada correctamente!");
            player.sendMessage("§7ID: §f#" + listingId);
            player.sendMessage("§7Precio de la pila: §a" + price);
            player.sendMessage(
                    "§7Duración: §f" + durationHours + " horas"
            );

        } catch (SQLException | IOException exception) {
            getLogger().error(
                    "Error al publicar un objeto de "
                            + player.getName() + ".",
                    exception
            );

            player.sendMessage(
                    "§cNo se pudo completar la publicación. "
                            + "Contacta con un administrador si el objeto "
                            + "no aparece en tu inventario."
            );

        } catch (RuntimeException exception) {
            getLogger().error(
                    "Error inesperado al publicar un objeto de "
                            + player.getName() + ".",
                    exception
            );

            player.sendMessage(
                    "§cOcurrió un error al publicar el objeto. "
                            + "Si desapareció de tu inventario, "
                            + "contacta con un administrador."
            );
        }
    }
}
