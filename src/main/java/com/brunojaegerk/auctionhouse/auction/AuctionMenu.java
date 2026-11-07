
package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import me.iwareq.fakeinventories.FakeInventory;
import com.brunojaegerk.auctionhouse.Main;
import com.brunojaegerk.auctionhouse.config.MessageManager;
import com.brunojaegerk.auctionhouse.storage.PendingItemManager;
import com.brunojaegerk.auctionhouse.storage.StorageMenu;
import com.formconstructor.form.ModalForm;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AuctionMenu {

    private static final int MAX_ITEMS_PER_PAGE = 45;

    private static final int MY_AUCTIONS_SLOT = 45;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;

    private final PluginBase plugin;
    private final MessageManager messageManager;
    private final AuctionManager auctionManager;
    private final AuctionPurchaseService purchaseService;
    private final StorageMenu storageMenu;

    public AuctionMenu(
            PluginBase plugin,
            AuctionManager auctionManager,
            AuctionPurchaseService purchaseService,
            PendingItemManager pendingItemManager
    ) {
        this.plugin = plugin;
        this.messageManager = ((Main) plugin).getMessageManager();
        this.auctionManager = auctionManager;
        this.purchaseService = purchaseService;

        this.storageMenu = new StorageMenu(
                plugin,
                pendingItemManager,
                this
        );
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

            String title = messageManager.getMenu(
                    ownAuctions
                            ? "auction.my-auctions-title"
                            : "auction.title"
            );

            FakeInventory inventory = new FakeInventory(
                    InventoryType.DOUBLE_CHEST,
                    title
            );

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
                            messageManager.getMenu(
                                    "auction.item.seller",
                                    Map.of("seller", auction.getSellerName())
                            ),
                            messageManager.getMenu(
                                    "auction.item.price",
                                    Map.of(
                                            "price",
                                            String.format(
                                                    Locale.US,
                                                    "%.2f",
                                                    auction.getPrice()
                                            )
                                    )
                            ),
                            messageManager.getMenu(
                                    "auction.item.auction-id",
                                    Map.of("id", auction.getId())
                            ),
                            "",
                            messageManager.getMenu(
                                    ownAuctions
                                            ? "auction.item.own-listing"
                                            : "auction.item.buy-hint"
                            )
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
                                    messageManager.getChat(
                                            "auction.own-listing"
                                    )
                            );
                            return;
                        }

                        buyer.removeWindow(inventory);

                        plugin.getServer().getScheduler().scheduleDelayedTask(
                                plugin,
                                () -> {
                                    if (buyer.isOnline()) {
                                        openPurchaseConfirmation(buyer, auction);
                                    }
                                },
                                10
                        );
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
                                messageManager.getMenu(
                                        ownAuctions
                                                ? "auction.empty.own-auctions"
                                                : "auction.empty.all-auctions"
                                )
                        )
                );
            }

            // Casilla 45: Mis subastas / volver al listado general.
            Item myAuctionsButton = Item.get(54);

            myAuctionsButton.setCustomName(
                    messageManager.getMenu(
                            ownAuctions
                                    ? "auction.buttons.back-to-auctions"
                                    : "auction.buttons.my-auctions"
                    )
            );

            myAuctionsButton.setLore(
                    messageManager.getMenuList(
                            ownAuctions
                                    ? "auction.lore.back-to-auctions"
                                    : "auction.lore.my-auctions"
                    ).toArray(new String[0])
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

            // Casilla 46: Storage.
            Item storageButton = Item.get(130);

            storageButton.setCustomName(
                    messageManager.getMenu("auction.buttons.storage")
            );

            storageButton.setLore(
                    messageManager.getMenuList(
                            "auction.lore.storage"
                    ).toArray(new String[0])
            );

            inventory.setItem(46, storageButton, (item, event) -> {
                event.setCancelled(true);

                Player clicker = event.getTransaction().getSource();

                if (clicker == null || !clicker.isOnline()) {
                    return;
                }

                clicker.removeWindow(inventory);

                plugin.getServer().getScheduler().scheduleDelayedTask(
                        plugin,
                        () -> {
                            if (clicker.isOnline()) {
                                storageMenu.open(clicker);
                            }
                        },
                        10
                );
            });

            // Casilla 49: información.
            Item infoButton = Item.get(339);
            infoButton.setCustomName(
                    messageManager.getMenu(
                            ownAuctions
                                    ? "auction.info.my-auctions-title"
                                    : "auction.info.auctions-title"
                    )
            );

            infoButton.setLore(
                    messageManager.getMenu(
                            "auction.info.page",
                            Map.of(
                                    "page", currentPage + 1,
                                    "pages", totalPages
                            )
                    ),
                    messageManager.getMenu(
                            "auction.info.listings",
                            Map.of("count", auctions.size())
                    ),
                    "",
                    messageManager.getMenu(
                            ownAuctions
                                    ? "auction.info.my-auctions-description"
                                    : "auction.info.auctions-description"
                    )
            );

            inventory.setItem(PAGE_INFO_SLOT, infoButton, (item, event) -> {
                event.setCancelled(true);

                Player clicker = event.getTransaction().getSource();

                if (clicker == null || !clicker.isOnline()) {
                    return;
                }

                clicker.sendMessage(
                        messageManager.getChat("auction.info.chat-title")
                );
                clicker.sendMessage(
                        messageManager.getChat(
                                "auction.info.chat-page",
                                Map.of(
                                        "page", currentPage + 1,
                                        "pages", totalPages
                                )
                        )
                );
                clicker.sendMessage(
                        messageManager.getChat(
                                "auction.info.chat-listings",
                                Map.of("count", auctions.size())
                        )
                );
            });

            // Casilla 48: página anterior.
            Item previousButton = Item.get(262);

            previousButton.setCustomName(
                    messageManager.getMenu(
                            currentPage > 0
                                    ? "auction.buttons.previous-page"
                                    : "auction.buttons.no-previous-page"
                    )
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
                    messageManager.getMenu(
                            currentPage < totalPages - 1
                                    ? "auction.buttons.next-page"
                                    : "auction.buttons.no-next-page"
                    )
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

            // Abrir la interfaz conservando el retraso de 10 ticks.
            plugin.getServer().getScheduler().scheduleDelayedTask(
                    plugin,
                    () -> {
                        if (!player.isOnline()) {
                            return;
                        }

                        player.addWindow(inventory);
                    },
                    10
            );

        } catch (SQLException exception) {
            plugin.getLogger().error(
                    "Error al cargar las subastas.",
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("auction.load-error")
            );
        }
    }

    private void openPurchaseConfirmation(
            Player buyer,
            Auction auction
    ) {
        if (buyer == null || !buyer.isOnline()) {
            return;
        }

        final Item item;

        try {
            item = ItemSerializer.deserialize(
                    auction.getItemData()
            );
        } catch (IOException exception) {
            plugin.getLogger().error(
                    "No se pudo reconstruir el objeto de la subasta "
                            + auction.getId(),
                    exception
            );

            buyer.sendMessage(
                    messageManager.getChat(
                            "auction.purchase.display-error"
                    )
            );
            return;
        }

        String price = String.format(
                Locale.US,
                "%.2f",
                auction.getPrice()
        );

        String content = messageManager.getMenu(
                "purchase.content",
                Map.of(
                        "item", item.getName(),
                        "amount", item.getCount(),
                        "seller", auction.getSellerName(),
                        "price", price
                )
        );

        ModalForm form = new ModalForm(
                messageManager.getMenu("purchase.title"),
                content
        );

        form.setPositiveButton(
                messageManager.getMenu("purchase.confirm")
        );
        form.setNegativeButton(
                messageManager.getMenu("purchase.cancel")
        );

        form.setHandler((player, response) -> {

            if (!player.isOnline()) {
                return;
            }

            if (response) {
                player.sendMessage(
                        messageManager.getChat(
                                "auction.purchase.processing"
                        )
                );

                purchaseService.purchase(
                        player,
                        auction.getId()
                );

            } else {
                player.sendMessage(
                        messageManager.getChat(
                                "auction.purchase.cancelled"
                        )
                );
            }
        });

        form.send(buyer);
    }
}



