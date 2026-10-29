package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import me.iwareq.fakeinventories.FakeInventory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

public final class AuctionMenu {

    private static final int MAX_ITEMS_PER_PAGE = 27;

    private final AuctionManager auctionManager;

    public AuctionMenu(AuctionManager auctionManager) {
        if (auctionManager == null) {
            throw new IllegalArgumentException(
                    "AuctionManager no puede ser null."
            );
        }

        this.auctionManager = auctionManager;
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        player.sendMessage(
                "§e[AH DEBUG] Comando recibido; preparando menú."
        );

        FakeInventory inventory = new FakeInventory(
                InventoryType.CHEST,
                "§8Casa de subastas"
        );

        // Evita que los jugadores muevan objetos de la interfaz.
        inventory.setDefaultItemHandler(
                (item, event) -> event.setCancelled(true)
        );

        try {
            List<Auction> auctions =
                    auctionManager.getActiveAuctions();

            player.sendMessage(
                    "§e[AH DEBUG] Subastas encontradas: "
                            + auctions.size()
            );

            int slot = 0;

            for (Auction auction : auctions) {
                if (slot >= MAX_ITEMS_PER_PAGE) {
                    break;
                }

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
                            "§eCompra próximamente"
                    );

                    inventory.setItem(slot, displayItem);
                    slot++;

                } catch (IOException | IllegalArgumentException exception) {
                    System.err.println(
                            "[AuctionHouse] No se pudo mostrar la subasta "
                                    + auction.getId() + ": "
                                    + exception.getMessage()
                    );
                }
            }

            if (auctions.isEmpty()) {
                inventory.setItem(
                        13,
                        Item.get(323).setCustomName(
                                "§cNo hay subastas activas"
                        )
                );
            }

            player.sendMessage(
                    "§e[AH DEBUG] Intentando abrir inventario."
            );

            int windowId = player.addWindow(inventory);

            player.sendMessage(
                    "§e[AH DEBUG] Resultado de addWindow: " + windowId
            );

        } catch (SQLException exception) {
            System.err.println(
                    "[AuctionHouse] Error al cargar las subastas: "
                            + exception.getMessage()
            );

            exception.printStackTrace();

            player.sendMessage(
                    "§cNo se pudieron cargar las subastas."
            );
        }
    }
}
