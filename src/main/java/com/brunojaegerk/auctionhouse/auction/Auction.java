package com.brunojaegerk.auctionhouse.auction;

import java.util.UUID;

public final class Auction {

    public enum Status {
        PENDING,
        ACTIVE,
        PURCHASE_PENDING,
        SOLD,
        EXPIRED,
        CANCELLED
    }

    private final long id;
    private final UUID sellerUUID;
    private final String sellerName;
    private final byte[] itemData;
    private final double price;
    private final String currencyId;
    private final long createdAt;
    private final long expiresAt;

    private Status status;

    public Auction(
            long id,
            UUID sellerUUID,
            String sellerName,
            byte[] itemData,
            double price,
            long createdAt,
            long expiresAt,
            Status status
    ) {
        this(id, sellerUUID, sellerName, itemData, price, "economyapi", createdAt, expiresAt, status);
    }

    public Auction(
            long id,
            UUID sellerUUID,
            String sellerName,
            byte[] itemData,
            double price,
            String currencyId,
            long createdAt,
            long expiresAt,
            Status status
    ) {
        if (sellerUUID == null) {
            throw new IllegalArgumentException(
                    "El UUID del vendedor no puede ser null."
            );
        }

        if (sellerName == null || sellerName.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del vendedor no puede estar vacío."
            );
        }

        if (itemData == null || itemData.length == 0) {
            throw new IllegalArgumentException(
                    "Los datos del objeto no pueden estar vacíos."
            );
        }

        if (!Double.isFinite(price) || price <= 0) {
            throw new IllegalArgumentException(
                    "El precio debe ser un número positivo y finito."
            );
        }

        if (currencyId == null || currencyId.isBlank()) {
            throw new IllegalArgumentException(
                    "La moneda de la publicación no puede estar vacía."
            );
        }

        if (createdAt < 0 || expiresAt <= createdAt) {
            throw new IllegalArgumentException(
                    "Las fechas de la publicación no son válidas."
            );
        }

        if (status == null) {
            throw new IllegalArgumentException(
                    "El estado de la publicación no puede ser null."
            );
        }

        this.id = id;
        this.sellerUUID = sellerUUID;
        this.sellerName = sellerName;
        this.itemData = itemData.clone();
        this.price = price;
        this.currencyId = currencyId.trim().toLowerCase(java.util.Locale.ROOT);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = status;
    }

    public long getId() {
        return id;
    }

    public UUID getSellerUUID() {
        return sellerUUID;
    }

    public String getSellerName() {
        return sellerName;
    }

    public byte[] getItemData() {
        return itemData.clone();
    }

    public double getPrice() {
        return price;
    }

    public String getCurrencyId() {
        return currencyId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isExpired(long currentTimeMillis) {
        return currentTimeMillis >= expiresAt;
    }

    public void setStatus(Status status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "El estado no puede ser null."
            );
        }

        this.status = status;
    }
}

