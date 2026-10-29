package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import com.brunojaegerk.auctionhouse.storage.DatabaseManager;
import com.brunojaegerk.auctionhouse.storage.PendingItemManager;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class AuctionService {

    private final DatabaseManager databaseManager;
    private final AuctionManager auctionManager;
    private final PendingItemManager pendingItemManager;

    public AuctionService(
            DatabaseManager databaseManager,
            AuctionManager auctionManager,
            PendingItemManager pendingItemManager
    ) {
        if (databaseManager == null
                || auctionManager == null
                || pendingItemManager == null) {
            throw new IllegalArgumentException(
                    "Los administradores son obligatorios."
            );
        }

        this.databaseManager = databaseManager;
        this.auctionManager = auctionManager;
        this.pendingItemManager = pendingItemManager;
    }

    public long prepareListing(
            Player player,
            double price,
            long durationMillis
    ) throws SQLException, IOException {

        if (player == null) {
            throw new IllegalArgumentException(
                    "El jugador no puede ser null."
            );
        }

        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException(
                    "El precio debe ser positivo y finito."
            );
        }

        if (durationMillis <= 0) {
            throw new IllegalArgumentException(
                    "La duración debe ser positiva."
            );
        }

        long now = System.currentTimeMillis();

        if (durationMillis > Long.MAX_VALUE - now) {
            throw new IllegalArgumentException(
                    "La duración de la publicación es demasiado grande."
            );
        }

        Item heldItem = player.getInventory().getItemInHand();

        if (heldItem == null || heldItem.isNull()
                || heldItem.getCount() <= 0) {
            throw new IllegalArgumentException(
                    "Debes tener un objeto en la mano."
            );
        }

        UUID sellerUUID = player.getUniqueId();
        String sellerName = player.getName();

        byte[] itemData = ItemSerializer.serialize(heldItem);

        Auction pendingAuction = new Auction(
                0,
                sellerUUID,
                sellerName,
                itemData,
                price,
                now,
                now + durationMillis,
                Auction.Status.PENDING
        );

        // 1. Guardar la publicación antes de modificar el inventario.
        long listingId = auctionManager.createAuction(pendingAuction);

        // 2. Retirar el objeto publicado.
        try {
            removeItemFromHand(player, heldItem);
        } catch (RuntimeException exception) {
            // El objeto no se retiró correctamente.
            // Cancelar la publicación sin crear una devolución duplicada.
            try {
                auctionManager.cancelPending(listingId);
            } catch (SQLException cancelException) {
                exception.addSuppressed(cancelException);
            }

            throw exception;
        }

        // 3. Activar la publicación.
        try {
            if (auctionManager.activatePending(listingId)) {
                return listingId;
            }

            throw new SQLException(
                    "La publicación no pudo activarse."
            );

        } catch (SQLException exception) {
            // Si continúa pendiente, guardar el objeto para recuperarlo.
            try {
                boolean recovered =
                        auctionManager.cancelPendingAndSaveItem(listingId);

                if (!recovered) {
                    exception.addSuppressed(
                            new SQLException(
                                    "No se pudo confirmar la recuperación "
                                            + "del objeto de la publicación "
                                            + listingId
                            )
                    );
                }
            } catch (SQLException recoveryException) {
                exception.addSuppressed(recoveryException);
            }

            throw exception;
        }
    }

    public boolean activateListing(long listingId) throws SQLException {
        return auctionManager.activatePending(listingId);
    }

    public boolean cancelPendingListing(long listingId) throws SQLException {
        return auctionManager.cancelPending(listingId);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuctionManager getAuctionManager() {
        return auctionManager;
    }


    public long savePendingItem(
            UUID ownerUUID,
            Item item,
            String reason
    ) throws SQLException, IOException {

        if (ownerUUID == null) {
            throw new IllegalArgumentException(
                    "El UUID del propietario es obligatorio."
            );
        }

        if (item == null || item.isNull() || item.getCount() <= 0) {
            throw new IllegalArgumentException(
                    "El objeto que se guardará no es válido."
            );
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "Debes indicar el motivo del objeto pendiente."
            );
        }

        byte[] itemData = ItemSerializer.serialize(item);

        return pendingItemManager.save(
                ownerUUID,
                itemData,
                reason
        );
    }

    private void removeItemFromHand(Player player, Item originalItem) {
        Item handItem = player.getInventory().getItemInHand();

        if (handItem == null || handItem.isNull()
                || handItem.getId() != originalItem.getId()
                || handItem.getDamage() != originalItem.getDamage()
                || handItem.getCount() < originalItem.getCount()) {
            throw new IllegalStateException(
                    "El objeto de la mano cambió durante la publicación."
            );
        }

        player.getInventory().setItemInHand(Item.get(0));
    }
}

