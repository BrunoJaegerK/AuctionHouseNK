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
import java.util.UUID;

public final class AuctionMenu {

    private static final int MAX_ITEMS_PER_PAGE = 45;

    private static final int MY_AUCTIONS_SLOT = 45;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;

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
        open(player, 0, false);
    }

    private void open(
            Player player,
            int requestedPage,
            boolean ownAuctions
    ) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            List<Auction> auctions;

            if (ownAuctions) {
                UUID playerUUID = player.getUniqueId();
                auctions = auctionManager.getActiveAuctionsBySeller(
                        playerUUID
                );
            } else {
                auctions = auctionManager.getActiveAuctions();
            }

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

            String title = ownAuctions
                    ? "§8Mis subastas"
                    : "§8Casa de subastas";

            FakeInventory inventory = new FakeInventory(
                    InventoryType.DOUBLE_CHEST,
                    title
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

            // Mostrar las subastas de la página seleccionada.
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
                            ownAuctions
                                    ? "§7Esta es tu publicación."
                                    : "§aHaz clic para comprar"
                    );

                    final long auctionId = auction.getId();

                    inventory.setItem(slot, displayItem, (item, event) -> {
                        event.setCancelled(true);

                        Player buyer = event.getTransaction().getSource();

                        if (buyer == null || !buyer.isOnline()) {
                            return;
                        }

                        if (ownAuctions) {
                            buyer.sendMessage(
                                    "§eEsta es tu propia subasta."
                            );
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
                                ownAuctions
                                        ? "§cNo tienes subastas activas"
                                        : "§cNo hay subastas activas"
                        )
                );
            }

            // Casilla 45: Mis subastas / volver al listado general.
            Item myAuctionsButton = Item.get(54);

            myAuctionsButton.setCustomName(
                    ownAuctions
                            ? "§eVolver a todas las subastas"
                            : "§aMis subastas"
            );

            myAuctionsButton.setLore(
                    ownAuctions
                            ? "§7Regresar al mercado general."
                            : "§7Ver tus publicaciones activas.",
                    "",
                    "§eHaz clic para abrir."
            );

            inventory.setItem(
                    MY_AUCTIONS_SLOT,
                    myAuctionsButton,
                    (item, event) -> {
                        event.setCancelled(true);

                        Player clicker = event.getTransaction().getSource();

                        if (clicker == null || !clicker.isOnline()) {
                            return;
                        }

                        boolean targetOwnAuctions = !ownAuctions;

                        clicker.removeWindow(inventory);

                        plugin.getServer().getScheduler().scheduleDelayedTask(
                                plugin,
                                () -> {
                                    if (!clicker.isOnline()) {
                                        return;
                                    }

                                    open(clicker, 0, targetOwnAuctions);
                                },
                                10
                        );
                    }
            );

            // Casilla 49: información.
            Item infoButton = Item.get(339);
            infoButton.setCustomName(
                    ownAuctions
                            ? "§bInformación de mis subastas"
                            : "§bInformación de subastas"
            );

            infoButton.setLore(
                    "§7Página: §f" + (currentPage + 1)
                            + "§7/§f" + totalPages,
                    "§7Publicaciones mostradas: §f" + auctions.size(),
                    "",
                    ownAuctions
                            ? "§7Aquí aparecen tus publicaciones activas."
                            : "§7Aquí aparecen las publicaciones activas."
            );

            inventory.setItem(PAGE_INFO_SLOT, infoButton, (item, event) -> {
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
                        "§7Publicaciones activas: §f" + auctions.size()
                );
            });

            // Casilla 48: página anterior.
            Item previousButton = Item.get(262);

            previousButton.setCustomName(
                    currentPage > 0
                            ? "§ePágina anterior"
                            : "§8No hay página anterior"
            );

            inventory.setItem(
                    PREVIOUS_PAGE_SLOT,
                    previousButton,
                    (item, event) -> {
                        event.setCancelled(true);

                        Player clicker =
                                event.getTransaction().getSource();

                        if (clicker == null || !clicker.isOnline()) {
                            return;
                        }

                        if (currentPage > 0) {
                            open(clicker, currentPage - 1, ownAuctions);
                        }
                    }
            );

            // Casilla 50: página siguiente.
            Item nextButton = Item.get(262);

            nextButton.setCustomName(
                    currentPage < totalPages - 1
                            ? "§ePágina siguiente"
                            : "§8No hay página siguiente"
            );

            inventory.setItem(
                    NEXT_PAGE_SLOT,
                    nextButton,
                    (item, event) -> {
                        event.setCancelled(true);

                        Player clicker =
                                event.getTransaction().getSource();

                        if (clicker == null || !clicker.isOnline()) {
                            return;
                        }

                        if (currentPage < totalPages - 1) {
                            open(clicker, currentPage + 1, ownAuctions);
                        }
                    }
            );

            player.sendMessage(
                    "§e[AH DEBUG] Intentando abrir inventario."
            );

            // Conservamos el retraso que ya funciona en tu servidor.
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
                    10
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


