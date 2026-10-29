package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import me.onebone.economyapi.EconomyAPI;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

public class AuctionPurchaseService {

    private final AuctionManager auctionManager;
    private final EconomyAPI economy;

    public AuctionPurchaseService(AuctionManager auctionManager) {
        this.auctionManager = auctionManager;
        this.economy = EconomyAPI.getInstance();
    }

    public boolean hasInventorySpace(PlayerInventory inventory, Item item) {
        if (inventory == null || item == null
                || item.getId() == 0 || item.getCount() <= 0) {
            return false;
        }

        int remaining = item.getCount();
        int maxStackSize = Math.min(
                item.getMaxStackSize(),
                inventory.getMaxStackSize()
        );

        if (maxStackSize <= 0) {
            return false;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Item existing = inventory.getItem(slot);

            if (existing == null || existing.getId() == 0
                    || existing.getCount() <= 0) {
                remaining -= maxStackSize;
            } else if (existing.equals(item, true, true)) {
                int stackLimit = Math.min(
                        existing.getMaxStackSize(),
                        inventory.getMaxStackSize()
                );

                remaining -= Math.max(
                        0, stackLimit - existing.getCount()
                );
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    public boolean reserveAuction(long auctionId) throws SQLException {
        return auctionManager.reservePurchase(auctionId);
    }

    public boolean releaseAuction(long auctionId) throws SQLException {
        return auctionManager.releasePurchase(auctionId);
    }

    /**
     * Intenta comprar una subasta y entregar el artículo directamente
     * al inventario del comprador.
     */
    public void purchase(Player buyer, long auctionId) {
        if (buyer == null || !buyer.isOnline()) {
            return;
        }

        UUID buyerUUID = buyer.getUniqueId();
        boolean reserved = false;
        boolean charged = false;
        boolean delivered = false;
        boolean sellerPaid = false;
        Auction auction = null;

        PlayerInventory inventory = buyer.getInventory();
        Item[] originalInventory = snapshotInventory(inventory);

        try {
            auction = auctionManager.findById(auctionId).orElse(null);

            if (auction == null || !auction.isActive()
                    || auction.isExpired(System.currentTimeMillis())) {
                buyer.sendMessage("§cEsta subasta ya no está disponible.");
                return;
            }

            UUID sellerUUID = auction.getSellerUUID();

            if (buyerUUID.equals(sellerUUID)) {
                buyer.sendMessage("§cNo puedes comprar tu propia subasta.");
                return;
            }

            if (!Double.isFinite(auction.getPrice())
                    || auction.getPrice() <= 0) {
                buyer.sendMessage("§cEl precio de esta subasta no es válido.");
                return;
            }

            Item item = ItemSerializer.deserialize(auction.getItemData());

            if (item == null || item.getId() == 0
                    || item.getCount() <= 0) {
                buyer.sendMessage("§cNo se pudo validar el artículo.");
                return;
            }

            if (!hasInventorySpace(inventory, item)) {
                buyer.sendMessage(
                        "§cNo tienes espacio suficiente en tu inventario."
                );
                return;
            }

            if (!economy.hasAccount(buyerUUID)
                    || !economy.hasAccount(sellerUUID)) {
                buyer.sendMessage(
                        "§cNo se pudo verificar la cuenta económica del comprador o vendedor."
                );
                return;
            }

            // Reserva atómica: solo un comprador puede obtenerla.
            if (!auctionManager.reservePurchase(auctionId)) {
                buyer.sendMessage(
                        "§cOtro jugador está comprando esta subasta "
                                + "o ya no está disponible."
                );
                return;
            }
            reserved = true;

            // Verifica que los datos sigan correspondiendo a la reserva.
            auction = auctionManager.findById(auctionId).orElse(null);

            if (auction == null
                    || auction.getStatus() != Auction.Status.PURCHASE_PENDING
                    || auction.isExpired(System.currentTimeMillis())) {
                buyer.sendMessage(
                        "§cLa subasta dejó de estar disponible."
                );
                return;
            }

            // Vuelve a comprobar el inventario antes de cobrar.
            item = ItemSerializer.deserialize(auction.getItemData());

            if (!hasInventorySpace(inventory, item)) {
                buyer.sendMessage(
                        "§cNo tienes espacio suficiente en tu inventario."
                );
                return;
            }

            // Cobra al comprador.
            int debitResult = economy.reduceMoney(
                    buyerUUID, auction.getPrice()
            );

            if (debitResult != EconomyAPI.RET_SUCCESS) {
                buyer.sendMessage(
                        "§cNo se pudo realizar el cobro. "
                                + "Verifica tu saldo e inténtalo de nuevo."
                );
                return;
            }
            charged = true;

            // Entrega directamente el artículo.
            delivered = deliverItem(inventory, item);

            if (!delivered) {
                buyer.sendMessage(
                        "§cNo se pudo entregar el artículo. "
                                + "Se intentará devolver el cobro."
                );
                return;
            }

            // Paga al vendedor.
            int creditResult = economy.addMoney(
                    auction.getSellerUUID(), auction.getPrice()
            );

            if (creditResult != EconomyAPI.RET_SUCCESS) {
                buyer.sendMessage(
                        "§cNo se pudo completar el pago al vendedor. "
                                + "Se intentará revertir la compra."
                );
                return;
            }
            sellerPaid = true;

            // Confirma la venta únicamente después de las operaciones previas.
            if (!auctionManager.completePurchase(auctionId)) {
                buyer.sendMessage(
                        "§cNo se pudo confirmar la venta en la base de datos. "
                                + "Se intentará revertir la compra."
                );
                return;
            }

            // La transacción terminó.
            reserved = false;
            charged = false;
            delivered = false;
            sellerPaid = false;

            buyer.sendMessage(
                    "§aHas comprado el artículo de la subasta §e#"
                            + auctionId + "§a por §e"
                            + String.format(java.util.Locale.US, "%.2f",
                            auction.getPrice()) + "§a."
            );

            return;

        } catch (SQLException | IOException exception) {
            buyer.sendMessage(
                    "§cOcurrió un error al procesar la compra. "
                            + "Se intentará revertir la operación."
            );
            exception.printStackTrace();

        } finally {
            /*
             * Si la compra no terminó, intenta deshacer las operaciones.
             * El resultado de cada reversión se comprueba en el registro.
             */
            if (auction != null) {
                UUID sellerUUID = auction.getSellerUUID();
                double price = auction.getPrice();

                if (delivered) {
                    if (!restoreInventory(inventory, originalInventory)) {
                        System.err.println(
                                "[AuctionHouse] No se pudo restaurar "
                                        + "completamente el inventario de "
                                        + buyer.getName()
                        );
                    }
                }

                if (sellerPaid) {
                    int result = economy.reduceMoney(sellerUUID, price);

                    if (result != EconomyAPI.RET_SUCCESS) {
                        System.err.println(
                                "[AuctionHouse] No se pudo revertir "
                                        + "el pago al vendedor de la subasta #"
                                        + auctionId
                        );
                    }
                }

                if (charged) {
                    int result = economy.addMoney(buyer.getUniqueId(), price);

                    if (result != EconomyAPI.RET_SUCCESS) {
                        System.err.println(
                                "[AuctionHouse] No se pudo devolver el cobro "
                                        + "al comprador de la subasta #"
                                        + auctionId
                        );
                        buyer.sendMessage(
                                "§cAtención: no se pudo devolver el cobro "
                                        + "automáticamente. Contacta con un administrador."
                        );
                    }
                }
            }

            if (reserved) {
                try {
                    if (!auctionManager.releasePurchase(auctionId)) {
                        System.err.println(
                                "[AuctionHouse] No se pudo liberar la reserva "
                                        + "de la subasta #" + auctionId
                        );
                    }
                } catch (SQLException exception) {
                    System.err.println(
                            "[AuctionHouse] Error liberando la reserva de #"
                                    + auctionId
                    );
                    exception.printStackTrace();
                }
            }
        }
    }

    private Item[] snapshotInventory(PlayerInventory inventory) {
        Item[] snapshot = new Item[inventory.getSize()];

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            snapshot[slot] = inventory.getItem(slot).clone();
        }

        return snapshot;
    }

    /**
     * Distribuye las unidades en pilas compatibles y espacios vacíos.
     * Si alguna modificación falla, intenta restaurar el inventario.
     */
    private boolean deliverItem(PlayerInventory inventory, Item item) {
        Item[] before = snapshotInventory(inventory);
        int remaining = item.getCount();

        // Primera pasada: completar pilas compatibles.
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            Item existing = inventory.getItem(slot);

            if (existing == null || existing.getId() == 0
                    || existing.getCount() <= 0
                    || !existing.equals(item, true, true)) {
                continue;
            }

            int limit = Math.min(
                    existing.getMaxStackSize(),
                    inventory.getMaxStackSize()
            );
            int amount = Math.min(
                    remaining, Math.max(0, limit - existing.getCount())
            );

            if (amount > 0) {
                Item updated = existing.clone();
                updated.setCount(existing.getCount() + amount);

                if (!inventory.setItem(slot, updated)) {
                    restoreInventory(inventory, before);
                    return false;
                }

                remaining -= amount;
            }
        }

        // Segunda pasada: usar espacios vacíos.
        int maxStackSize = Math.min(
                item.getMaxStackSize(),
                inventory.getMaxStackSize()
        );

        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            Item existing = inventory.getItem(slot);

            if (existing != null && existing.getId() != 0
                    && existing.getCount() > 0) {
                continue;
            }

            int amount = Math.min(remaining, maxStackSize);
            Item stack = item.clone();
            stack.setCount(amount);

            if (!inventory.setItem(slot, stack)) {
                restoreInventory(inventory, before);
                return false;
            }

            remaining -= amount;
        }

        if (remaining > 0) {
            restoreInventory(inventory, before);
            return false;
        }

        return true;
    }

    private boolean restoreInventory(
            PlayerInventory inventory, Item[] snapshot) {
        boolean restored = true;

        for (int slot = 0; slot < snapshot.length; slot++) {
            if (!inventory.setItem(slot, snapshot[slot].clone())) {
                restored = false;
            }
        }

        return restored;
    }
}


