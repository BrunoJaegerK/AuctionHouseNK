package com.brunojaegerk.auctionhouse.storage;

import java.util.UUID;

public final class PendingItem {

    private final long id;
    private final UUID ownerUUID;
    private final byte[] itemData;
    private final String reason;
    private final long createdAt;

    public PendingItem(
            long id,
            UUID ownerUUID,
            byte[] itemData,
            String reason,
            long createdAt
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException("El ID debe ser positivo.");
        }
        if (ownerUUID == null) {
            throw new IllegalArgumentException("El UUID es obligatorio.");
        }
        if (itemData == null || itemData.length == 0) {
            throw new IllegalArgumentException("Los datos del objeto están vacíos.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio.");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("La fecha no es válida.");
        }

        this.id = id;
        this.ownerUUID = ownerUUID;
        this.itemData = itemData.clone();
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public byte[] getItemData() {
        return itemData.clone();
    }

    public String getReason() {
        return reason;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

