package com.brunojaegerk.auctionhouse.storage;

import cn.nukkit.Player;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.plugin.PluginBase;
import me.iwareq.fakeinventories.FakeInventory;

import com.brunojaegerk.auctionhouse.Main;
import com.brunojaegerk.auctionhouse.auction.AuctionMenu;
import com.brunojaegerk.auctionhouse.auction.ItemSerializer;
import com.brunojaegerk.auctionhouse.config.MessageManager;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public final class StorageMenu {

    private static final int MAX_ITEMS_PER_PAGE = 45;

    private static final int BACK_SLOT = 45;
    private static final int PREVIOUS_PAGE_SLOT = 48;
    private static final int PAGE_INFO_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 50;

    private final PluginBase plugin;
    private final MessageManager messageManager;
    private final PendingItemManager pendingItemManager;
    private final AuctionMenu auctionMenu;

    public StorageMenu(
            PluginBase plugin,
            PendingItemManager pendingItemManager,
            AuctionMenu auctionMenu
    ) {
        this.plugin = plugin;
        this.messageManager = ((Main) plugin).getMessageManager();
        this.pendingItemManager = pendingItemManager;
        this.auctionMenu = auctionMenu;
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void open(Player player, int requestedPage) {
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            List<PendingItem> items =
                    pendingItemManager.getByOwner(player.getUniqueId());

            int totalPages = Math.max(
                    1,
                    (int) Math.ceil(
                            (double) items.size() / MAX_ITEMS_PER_PAGE
                    )
            );

            final int currentPage = Math.max(
                    0,
                    Math.min(requestedPage, totalPages - 1)
            );

            int startIndex = currentPage * MAX_ITEMS_PER_PAGE;
            int endIndex = Math.min(
                    startIndex + MAX_ITEMS_PER_PAGE,
                    items.size()
            );

            FakeInventory inventory = new FakeInventory(
                    InventoryType.DOUBLE_CHEST,
                    messageManager.getMenu("storage.title")
            );

            inventory.setDefaultItemHandler(
                    (item, event) -> event.setCancelled(true)
            );

            int slot = 0;

            for (int index = startIndex; index < endIndex; index++) {
                PendingItem pendingItem = items.get(index);

                try {
                    Item displayItem = ItemSerializer.deserialize(
                            pendingItem.getItemData()
                    );

                    displayItem.setLore(
                            messageManager.getMenu(
                                    "storage.item.reason",
                                    Map.of(
                                            "reason",
                                            pendingItem.getReason()
                                    )
                            ),
                            "",
                            messageManager.getMenu("storage.item.claim-hint")
                    );

                    long pendingId = pendingItem.getId();

                    inventory.setItem(slot, displayItem, (item, event) -> {
                        event.setCancelled(true);

                        Player clicker =
                                event.getTransaction().getSource();

                        if (clicker == null || !clicker.isOnline()) {
                            return;
                        }

                        claimItem(clicker, inventory, pendingId, currentPage);
                    });

                    slot++;

                } catch (IOException | IllegalArgumentException exception) {
                    plugin.getLogger().warning(
                            "No se pudo mostrar el objeto pendiente "
                                    + pendingItem.getId() + ": "
                                    + exception.getMessage()
                    );
                }
            }

            if (items.isEmpty()) {
                inventory.setItem(
                        22,
                        Item.get(130).setCustomName(
                                messageManager.getMenu("storage.empty.title")
                        ).setLore(
                                messageManager.getMenuList(
                                        "storage.empty.lore"
                                ).toArray(new String[0])
                        )
                );
            }

            // Casilla 45: regresar al mercado.
            Item backButton = Item.get(54);
            backButton.setCustomName(
                    messageManager.getMenu("storage.buttons.back")
            );
            backButton.setLore(
                    messageManager.getMenuList(
                            "storage.lore.back"
                    ).toArray(new String[0])
            );

            inventory.setItem(BACK_SLOT, backButton, (item, event) -> {
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
                                auctionMenu.open(clicker);
                            }
                        },
                        10
                );
            });

            // Casilla 49: información.
            Item infoButton = Item.get(339);
            infoButton.setCustomName(
                    messageManager.getMenu("storage.info.title")
            );
            infoButton.setLore(
                    messageManager.getMenu(
                            "storage.info.items",
                            Map.of("count", items.size())
                    ),
                    messageManager.getMenu(
                            "storage.info.page",
                            Map.of(
                                    "page", currentPage + 1,
                                    "pages", totalPages
                            )
                    ),
                    "",
                    messageManager.getMenu("storage.info.description-line-1"),
                    messageManager.getMenu("storage.info.description-line-2")
            );

            inventory.setItem(PAGE_INFO_SLOT, infoButton);

            // Casilla 48: página anterior.
            Item previousButton = Item.get(262);
            previousButton.setCustomName(
                    messageManager.getMenu(
                            currentPage > 0
                                    ? "storage.buttons.previous-page"
                                    : "storage.buttons.no-previous-page"
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
                            changePage(clicker, inventory, currentPage - 1);
                        }
                    }
            );

            // Casilla 50: página siguiente.
            Item nextButton = Item.get(262);
            nextButton.setCustomName(
                    messageManager.getMenu(
                            currentPage < totalPages - 1
                                    ? "storage.buttons.next-page"
                                    : "storage.buttons.no-next-page"
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
                            changePage(clicker, inventory, currentPage + 1);
                        }
                    }
            );

            // Abrir Storage después de 10 ticks.
            plugin.getServer().getScheduler().scheduleDelayedTask(
                    plugin,
                    () -> {
                        if (player.isOnline()) {
                            player.addWindow(inventory);
                        }
                    },
                    10
            );

        } catch (SQLException exception) {
            plugin.getLogger().error(
                    "No se pudo cargar Storage para "
                            + player.getName(),
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("storage.load-error")
            );
        }
    }

    private void changePage(
            Player player,
            FakeInventory inventory,
            int page
    ) {
        player.removeWindow(inventory);

        plugin.getServer().getScheduler().scheduleDelayedTask(
                plugin,
                () -> {
                    if (player.isOnline()) {
                        open(player, page);
                    }
                },
                10
        );
    }

    private void claimItem(
            Player player,
            FakeInventory inventory,
            long pendingId,
            int currentPage
    ) {
        try {
            PendingItem pendingItem = null;

            for (PendingItem item :
                    pendingItemManager.getByOwner(player.getUniqueId())) {

                if (item.getId() == pendingId) {
                    pendingItem = item;
                    break;
                }
            }

            if (pendingItem == null) {
                player.sendMessage(
                        messageManager.getChat("storage.item-unavailable")
                );
                return;
            }

            Item item = ItemSerializer.deserialize(
                    pendingItem.getItemData()
            );

            if (!player.getInventory().canAddItem(item)) {
                player.sendMessage(
                        messageManager.getChat("storage.inventory-full")
                );
                return;
            }

            Item[] leftovers = player.getInventory().addItem(item);

            if (leftovers != null && leftovers.length > 0) {
                player.sendMessage(
                        messageManager.getChat("storage.delivery-incomplete")
                );
                return;
            }

            // El registro se elimina después de entregar el objeto.
            pendingItemManager.delete(
                    pendingId,
                    player.getUniqueId()
            );

            player.sendMessage(
                    messageManager.getChat("storage.claim-success")
            );

            // Actualizar Storage después de cerrar la ventana actual.
            // El nuevo menú se abrirá con su retraso de 10 ticks.
            player.removeWindow(inventory);

            plugin.getServer().getScheduler().scheduleDelayedTask(
                    plugin,
                    () -> {
                        if (player.isOnline()) {
                            open(player, currentPage);
                        }
                    },
                    10
            );

        } catch (IOException exception) {
            plugin.getLogger().error(
                    "No se pudo reconstruir el objeto pendiente "
                            + pendingId,
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("storage.recovery-error")
            );

        } catch (SQLException exception) {
            plugin.getLogger().error(
                    "No se pudo actualizar Storage para "
                            + player.getName(),
                    exception
            );

            player.sendMessage(
                    messageManager.getChat("storage.update-error")
            );
        }
    }
}

