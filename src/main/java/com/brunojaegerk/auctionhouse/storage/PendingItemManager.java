package com.brunojaegerk.auctionhouse.storage;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public final class PendingItemManager {

    private final DatabaseManager databaseManager;

    public PendingItemManager(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException(
                    "DatabaseManager es obligatorio."
            );
        }

        this.databaseManager = databaseManager;
    }

    public long save(
            UUID ownerUUID,
            byte[] itemData,
            String reason
    ) throws SQLException {
        return databaseManager.savePendingItem(
                ownerUUID,
                itemData,
                reason
        );
    }

    public List<PendingItem> getByOwner(
            UUID ownerUUID
    ) throws SQLException {
        return databaseManager.getPendingItems(ownerUUID);
    }

    public boolean delete(
            long itemId,
            UUID ownerUUID
    ) throws SQLException {
        return databaseManager.deletePendingItem(
                itemId,
                ownerUUID
        );
    }
}

