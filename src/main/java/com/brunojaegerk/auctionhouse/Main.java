
package com.brunojaegerk.auctionhouse;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;

import com.brunojaegerk.auctionhouse.auction.AuctionManager;
import com.brunojaegerk.auctionhouse.auction.AuctionMenu;
import com.brunojaegerk.auctionhouse.auction.AuctionPurchaseService;
import com.brunojaegerk.auctionhouse.auction.AuctionService;
import com.brunojaegerk.auctionhouse.auction.ItemSerializer;
import com.brunojaegerk.auctionhouse.storage.DatabaseManager;
import com.brunojaegerk.auctionhouse.storage.PendingItemManager;
import com.brunojaegerk.auctionhouse.auction.ListingLimitManager;
import com.brunojaegerk.auctionhouse.config.MessageManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;

public class Main extends PluginBase {

    private DatabaseManager databaseManager;
    private AuctionManager auctionManager;
    private AuctionService auctionService;
    private PendingItemManager pendingItemManager;
    private AuctionMenu auctionMenu;
    private AuctionPurchaseService auctionPurchaseService;
    private ListingLimitManager listingLimitManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        databaseManager = new DatabaseManager(this);
        listingLimitManager = new ListingLimitManager(this);
        messageManager = new MessageManager(this);

        try {
            databaseManager.initialize();

            auctionManager = new AuctionManager(databaseManager);

            pendingItemManager = new PendingItemManager(databaseManager);

            auctionPurchaseService =
                    new AuctionPurchaseService(auctionManager, messageManager);

            auctionMenu = new AuctionMenu(
                    this,
                    auctionManager,
                    auctionPurchaseService,
                    pendingItemManager
            );

            auctionService = new AuctionService(
                    databaseManager,
                    auctionManager,
                    pendingItemManager
            );

        } catch (SQLException exception) {
            getLogger().error(
                    "No se pudo inicializar H2. AuctionHouse se desactivará.",
                    exception
            );

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer().getScheduler().scheduleRepeatingTask(
                this,
                () -> {
                    try {
                        int expired = auctionManager.expireDueAuctions(
                                System.currentTimeMillis()
                        );

                        if (expired > 0) {
                            getLogger().info(
                                    "Se transfirieron " + expired
                                            + " publicaciones vencidas a Storage."
                            );
                        }

                    } catch (SQLException exception) {
                        getLogger().error(
                                "Error al procesar publicaciones vencidas.",
                                exception
                        );
                    }
                },
                20 * 60
        );

        getLogger().info(
                "AuctionHouse 1.0.0 se ha habilitado correctamente."
        );
        getLogger().info("Base de datos H2 conectada.");
        getLogger().info("Gestor de subastas inicializado.");
        getLogger().info("Gestor de objetos pendientes inicializado.");
        getLogger().info("Servicio de subastas inicializado.");
        getLogger().info("Servicio de compras inicializado.");
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

    public AuctionPurchaseService getAuctionPurchaseService() {
        return auctionPurchaseService;
    }

    public MessageManager getMessageManager() {
        return messageManager;
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
                    messageManager.getChat("auction.command.no-permission")
            );
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(
                    messageManager.getChat("general.player-only")
            );
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 1
                && args[0].equalsIgnoreCase("testitem")) {

            if (!player.hasPermission("auctionhouse.admin")) {
                player.sendMessage(
                        messageManager.getChat("auction.command.test-no-permission")
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
                        messageManager.getChat("auction.command.sell-no-permission")
                );
                return true;
            }

            sellItem(player, args[1]);
            return true;
        }

        if (args.length > 0
                && args[0].equalsIgnoreCase("sell")) {
            player.sendMessage(
                    messageManager.getChat("auction.command.sell-usage")
            );
            return true;
        }

        if (auctionMenu == null) {
            player.sendMessage(
                    messageManager.getChat("auction.command.unavailable")
            );
            return true;
        }

        auctionMenu.open(player);
        return true;
    }

    private void testItemSerialization(Player player) {
        Item original = player.getInventory().getItemInHand();

        if (original == null || original.isNull()) {
            player.sendMessage(
                    messageManager.getChat("auction.test.no-item")
            );
            return;
        }

        try {
            byte[] serialized = ItemSerializer.serialize(original);
            Item restored = ItemSerializer.deserialize(serialized);

            boolean sameId =
                    original.getId() == restored.getId();

            boolean sameDamage =
                    original.getDamage() == restored.getDamage();

            boolean sameCount =
                    original.getCount() == restored.getCount();

            boolean sameNbt = Arrays.equals(
                    original.getCompoundTag(),
                    restored.getCompoundTag()
            );

            if (sameId && sameDamage && sameCount && sameNbt) {
                player.sendMessage(
                        messageManager.getChat("auction.test.success")
                );
                player.sendMessage(
                        messageManager.getChat(
                                "auction.test.item",
                                Map.of("item", original.getName())
                        )
                );
                player.sendMessage(
                        messageManager.getChat(
                                "auction.test.amount",
                                Map.of("amount", original.getCount())
                        )
                );
                player.sendMessage(
                        messageManager.getChat("auction.test.nbt")
                );
            } else {
                player.sendMessage(
                        messageManager.getChat("auction.test.failed")
                );

                getLogger().warning(
                        "La serialización no coincide para "
                                + player.getName()
                );
            }

        } catch (IOException | IllegalArgumentException exception) {
            player.sendMessage(
                    messageManager.getChat("auction.test.serialization-error")
            );

            getLogger().error(
                    "Error de serialización de objetos.",
                    exception
            );
        }
    }

    private void sellItem(
            Player player,
            String priceArgument
    ) {
        final double price;

        try {
            price = Double.parseDouble(priceArgument);
        } catch (NumberFormatException exception) {
            player.sendMessage(
                    messageManager.getChat("auction.invalid-price")
            );
            return;
        }

        if (!Double.isFinite(price) || price <= 0) {
            player.sendMessage(
                    messageManager.getChat("auction.price-not-positive")
            );
            return;
        }

        double minPrice = getConfig().getDouble(
                "auction.min-price",
                1.0
        );

        double maxPrice = getConfig().getDouble(
                "auction.max-price",
                1000000000.0
        );

        if (price < minPrice || price > maxPrice) {
            player.sendMessage(
                    messageManager.getChat(
                            "auction.price-range",
                            Map.of(
                                    "min", minPrice,
                                    "max", maxPrice
                            )
                    )
            );
            return;
        }

        int maxListings = listingLimitManager.getMaxListings(player);

        if (auctionManager == null || auctionService == null) {
            player.sendMessage(
                    messageManager.getChat("auction.command.service-unavailable")
            );
            return;
        }

        try {
            int currentListings = auctionManager
                    .getActiveAuctionsBySeller(player.getUniqueId())
                    .size();

            if (maxListings > 0 && currentListings >= maxListings) {
                player.sendMessage(
                        messageManager.getChat(
                                "auction.listing-limit",
                                Map.of("limit", maxListings)
                        )
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
                        messageManager.getChat(
                                "auction.listing.duration-misconfigured"
                        )
                );
                return;
            }

            long durationMillis = durationHours * 3600000L;

            long listingId = auctionService.prepareListing(
                    player,
                    price,
                    durationMillis
            );

            player.sendMessage(
                    messageManager.getChat("auction.listing-created")
            );
            player.sendMessage(
                    messageManager.getChat(
                            "auction.listing-id",
                            Map.of("id", listingId)
                    )
            );
            player.sendMessage(
                    messageManager.getChat(
                            "auction.listing-price",
                            Map.of("price", price)
                    )
            );
            player.sendMessage(
                    messageManager.getChat(
                            "auction.listing-duration",
                            Map.of("hours", durationHours)
                    )
            );

        } catch (SQLException | IOException exception) {
            getLogger().error(
                    "Error al publicar un objeto de "
                            + player.getName() + ".",
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("auction.listing.error")
            );

        } catch (RuntimeException exception) {
            getLogger().error(
                    "Error inesperado al publicar un objeto de "
                            + player.getName() + ".",
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("auction.listing.unexpected-error")
            );
        }
    }
}


