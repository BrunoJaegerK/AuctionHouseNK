package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import me.iwareq.fakeinventories.FakeInventory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class AuctionMenu {

    private static final int MAX_ITEMS_PER_PAGE = 45;

    private final PluginBase plugin;
    private final AuctionManager auctionManager;
    private final AuctionPurchaseService purchaseService;

    public AuctionMenu(
            PluginBase plugin,
            AuctionManager auctionManager,
            AuctionPurchaseService purchaseService
    ) {
        this.plugin = plugin;
        this.auctionManager = auctionManager;
        this.purchaseService = purchaseService;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendMessage(
                "§e[AH DEBUG] Comando recibido; preparando menú."
        );

        try {
            List<Auction> auctions =
                    auctionManager.getActiveAuctions();

            int totalPages = Math.max(
                    1,
                    (int) Math.ceil(
                            (double) auctions.size() / MAX_ITEMS_PER_PAGE
                    )
            );

            final int currentPage = Math.max(
                    0,
                    Math.min(requestedPage, totalPages - 1)
            );

            int startIndex = currentPage * MAX_ITEMS_PER_PAGE;
            int endIndex = Math.min(
                    startIndex + MAX_ITEMS_PER_PAGE,
                    auctions.size()
            );

            FakeInventory inventory = new FakeInventory(
                    InventoryType.DOUBLE_CHEST,
                    "§8Casa de subastas"
            ) {
                @Override
                public void onOpen(Player player) {
                    player.sendMessage(
                            "§b[AH DEBUG] FakeInventory.onOpen ejecutado."
                    );

                    super.onOpen(player);

                    player.sendMessage(
                            "§b[AH DEBUG] FakeInventory.onOpen finalizado."
                    );
                }
            };

            inventory.setDefaultItemHandler(
                    (item, event) -> event.setCancelled(true)
            );

            player.sendMessage(
                    "§e[AH DEBUG] Subastas encontradas: "
                            + auctions.size()
            );

            // Artículos de la página actual.
            int slot = 0;

            for (int index = startIndex; index < endIndex; index++) {
                Auction auction = auctions.get(index);

                try {
                    Item displayItem = ItemSerializer.deserialize(
                            auction.getItemData()
                    );

                    displayItem.setLore(
                            "§7Vendedor: §f" + auction.getSellerName(),
                            "§7Precio: §6$" + String.format(
                                    Locale.US,
                                    "%.2f",
                                    auction.getPrice()
                            ),
                            "§8ID de subasta: " + auction.getId(),
                            "",
                            "§aHaz clic para comprar"
                    );

                    final long auctionId = auction.getId();

                    inventory.setItem(slot, displayItem, (item, event) -> {
                        event.setCancelled(true);

                        Player buyer = event.getTransaction().getSource();

                        if (buyer == null || !buyer.isOnline()) {
                            return;
                        }

                        purchaseService.purchase(buyer, auctionId);
                    });

                    slot++;

                } catch (IOException | IllegalArgumentException exception) {
                    plugin.getLogger().warning(
                            "No se pudo mostrar la subasta "
                                    + auction.getId() + ": "
                                    + exception.getMessage()
                    );
                }
            }

            if (auctions.isEmpty()) {
                inventory.setItem(
                        22,
                        Item.get(323).setCustomName(
                                "§cNo hay subastas activas"
                        )
                );
            }

            // Botón de información: casilla 49.
            Item infoButton = Item.get(339);
            infoButton.setCustomName("§bInformación de subastas");
            infoButton.setLore(
                    "§7Casa de subastas",
                    "§7Consulta los artículos disponibles.",
                    "",
                    "§7Página: §f" + (currentPage + 1)
                            + "§7/§f" + totalPages,
                    "§7Subastas activas: §f" + auctions.size(),
                    "",
                    "§eHaz clic para ver la información."
            );

            inventory.setItem(49, infoButton, (item, event) -> {
                event.setCancelled(true);

                Player clicker = event.getTransaction().getSource();

                if (clicker == null || !clicker.isOnline()) {
                    return;
                }

                clicker.sendMessage("§6§lCasa de subastas");
                clicker.sendMessage(
                        "§7Página: §f" + (currentPage + 1)
                                + "§7/§f" + totalPages
                );
                clicker.sendMessage(
                        "§7Subastas activas: §f" + auctions.size()
                );
            });

            // Botón de página anterior: casilla 48.
            Item previousButton = Item.get(262);

            if (currentPage > 0) {
                previousButton.setCustomName("§ePágina anterior");
                previousButton.setLore(
                        "§7Ir a la página " + currentPage
                );
            } else {
                previousButton.setCustomName(
                        "§8No hay página anterior"
                );
            }

            inventory.setItem(48, previousButton, (item, event) -> {
                event.setCancelled(true);

                Player clicker = event.getTransaction().getSource();

                if (clicker == null || !clicker.isOnline()) {
                    return;
                }

                if (currentPage > 0) {
                    open(clicker, currentPage - 1);
                }
            });

            // Botón de página siguiente: casilla 50.
            Item nextButton = Item.get(262);

            if (currentPage < totalPages - 1) {
                nextButton.setCustomName("§ePágina siguiente");
                nextButton.setLore(
                        "§7Ir a la página " + (currentPage + 2)
                );
            } else {
                nextButton.setCustomName(
                        "§8No hay página siguiente"
                );
            }

            inventory.setItem(50, nextButton, (item, event) -> {
                event.setCancelled(true);

                Player clicker = event.getTransaction().getSource();

                if (clicker == null || !clicker.isOnline()) {
                    return;
                }

                if (currentPage < totalPages - 1) {
                    open(clicker, currentPage + 1);
                }
            });

            player.sendMessage(
                    "§e[AH DEBUG] Intentando abrir inventario."
            );

            // Se conserva el retraso original que ya funcionaba.
            plugin.getServer().getScheduler().scheduleDelayedTask(
                    plugin,
                    () -> {
                        if (!player.isOnline()) {
                            return;
                        }

                        int windowId = player.addWindow(inventory);

                        player.sendMessage(
                                "§e[AH DEBUG] Resultado de addWindow: "
                                        + windowId
                        );
                    },
                    20
            );

        } catch (SQLException exception) {
            plugin.getLogger().error(
                    "Error al cargar las subastas.",
                    exception
            );

            player.sendMessage(
                    "§cNo se pudieron cargar las subastas."
            );
        }
    }
}

