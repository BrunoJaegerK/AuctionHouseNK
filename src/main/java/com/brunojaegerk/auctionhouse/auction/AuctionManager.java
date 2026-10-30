package com.brunojaegerk.auctionhouse.auction;

import com.brunojaegerk.auctionhouse.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AuctionManager {

    private final DatabaseManager databaseManager;

    public AuctionManager(DatabaseManager databaseManager) {
        if (databaseManager == null) {
            throw new IllegalArgumentException(
                    "DatabaseManager no puede ser null."
            );
        }

        this.databaseManager = databaseManager;
    }

    public long createAuction(Auction auction) throws SQLException {
        if (auction == null) {
            throw new IllegalArgumentException(
                    "La publicación no puede ser null."
            );
        }

        String sql = """
                INSERT INTO auction_listings (
                    seller_uuid,
                    seller_name,
                    item_data,
                    price,
                    created_at,
                    expires_at,
                    status
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        Connection connection = databaseManager.getConnection();

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                java.sql.Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, auction.getSellerUUID().toString());
            statement.setString(2, auction.getSellerName());
            statement.setBytes(3, auction.getItemData());
            statement.setDouble(4, auction.getPrice());
            statement.setLong(5, auction.getCreatedAt());
            statement.setLong(6, auction.getExpiresAt());
            statement.setString(7, auction.getStatus().name());

            int affectedRows = statement.executeUpdate();

            if (affectedRows != 1) {
                throw new SQLException(
                        "No se pudo guardar la publicación."
                );
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new SQLException(
                "SQLite no devolvió el ID de la publicación."
        );
    }

    public Optional<Auction> findById(long id) throws SQLException {
        String sql = """
                SELECT *
                FROM auction_listings
                WHERE id = ?
                """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setLong(1, id);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapAuction(result));
                }
            }
        }

        return Optional.empty();
    }

    public List<Auction> getActiveAuctions() throws SQLException {
        String sql = """
        SELECT *
        FROM auction_listings
        WHERE status = ?
          AND expires_at > ?
        ORDER BY created_at DESC, id DESC
        """;

        List<Auction> auctions = new ArrayList<>();

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, Auction.Status.ACTIVE.name());
            statement.setLong(2, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    auctions.add(mapAuction(result));
                }
            }
        }

        return auctions;
    }

    public List<Auction> getActiveAuctionsBySeller(UUID sellerUUID)
            throws SQLException {

        if (sellerUUID == null) {
            throw new IllegalArgumentException(
                    "El UUID del vendedor no puede ser null."
            );
        }

        String sql = """
        SELECT *
        FROM auction_listings
        WHERE seller_uuid = ?
          AND status = ?
          AND expires_at > ?
        ORDER BY created_at DESC, id DESC
        """;

        List<Auction> auctions = new ArrayList<>();

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, sellerUUID.toString());
            statement.setString(2, Auction.Status.ACTIVE.name());
            statement.setLong(3, System.currentTimeMillis());

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    auctions.add(mapAuction(result));
                }
            }
        }

        return auctions;
    }

    public boolean updateStatus(long id, Auction.Status newStatus)
            throws SQLException {

        if (newStatus == null) {
            throw new IllegalArgumentException(
                    "El nuevo estado no puede ser null."
            );
        }

        String sql = """
                UPDATE auction_listings
                SET status = ?
                WHERE id = ? AND status = ?
                """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, newStatus.name());
            statement.setLong(2, id);
            statement.setString(3, Auction.Status.ACTIVE.name());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean activatePending(long id) throws SQLException {
        String sql = """
            UPDATE auction_listings
            SET status = ?
            WHERE id = ? AND status = ?
            """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, Auction.Status.ACTIVE.name());
            statement.setLong(2, id);
            statement.setString(3, Auction.Status.PENDING.name());

            return statement.executeUpdate() == 1;
        }
    }

    private Auction mapAuction(ResultSet result) throws SQLException {
        UUID sellerUUID;

        try {
            sellerUUID = UUID.fromString(result.getString("seller_uuid"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "UUID inválido en la publicación "
                            + result.getLong("id"),
                    exception
            );
        }

        Auction.Status status;

        try {
            status = Auction.Status.valueOf(result.getString("status"));
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "Estado inválido en la publicación "
                            + result.getLong("id"),
                    exception
            );
        }

        try {
            return new Auction(
                    result.getLong("id"),
                    sellerUUID,
                    result.getString("seller_name"),
                    result.getBytes("item_data"),
                    result.getDouble("price"),
                    result.getLong("created_at"),
                    result.getLong("expires_at"),
                    status
            );
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                    "Datos inválidos en la publicación "
                            + result.getLong("id"),
                    exception
            );
        }
    }

    public boolean cancelPending(long id) throws SQLException {
        String sql = """
        UPDATE auction_listings
        SET status = 'CANCELLED'
        WHERE id = ? AND status = 'PENDING'
        """;

        Connection connection = databaseManager.getConnection();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() == 1;
        }
    }


    public boolean cancelPendingAndSaveItem(long listingId)
            throws SQLException {

        if (listingId <= 0) {
            throw new IllegalArgumentException(
                    "El ID de la publicación debe ser positivo."
            );
        }

        Connection connection = databaseManager.getConnection();
        boolean originalAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            String selectSql = """
                SELECT seller_uuid, item_data
                FROM auction_listings
                WHERE id = ? AND status = ?
                """;

            String sellerUUID;
            byte[] itemData;

            try (PreparedStatement select =
                         connection.prepareStatement(selectSql)) {

                select.setLong(1, listingId);
                select.setString(2, Auction.Status.PENDING.name());

                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) {
                        connection.rollback();
                        return false;
                    }

                    sellerUUID = result.getString("seller_uuid");
                    itemData = result.getBytes("item_data");
                }
            }

            if (sellerUUID == null || itemData == null || itemData.length == 0) {
                throw new SQLException(
                        "La publicación no contiene datos válidos para recuperar."
                );
            }

            String insertSql = """
                INSERT INTO pending_items (
                    owner_uuid,
                    item_data,
                    reason,
                    created_at
                ) VALUES (?, ?, ?, ?)
                """;

            try (PreparedStatement insert =
                         connection.prepareStatement(insertSql)) {

                insert.setString(1, sellerUUID);
                insert.setBytes(2, itemData);
                insert.setString(3, "CANCELLED_LISTING:" + listingId);
                insert.setLong(4, System.currentTimeMillis());

                if (insert.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo guardar el objeto pendiente."
                    );
                }
            }

            String updateSql = """
                UPDATE auction_listings
                SET status = ?
                WHERE id = ? AND status = ?
                """;

            try (PreparedStatement update =
                         connection.prepareStatement(updateSql)) {

                update.setString(1, Auction.Status.CANCELLED.name());
                update.setLong(2, listingId);
                update.setString(3, Auction.Status.PENDING.name());

                if (update.executeUpdate() != 1) {
                    throw new SQLException(
                            "No se pudo cancelar la publicación pendiente."
                    );
                }
            }

            connection.commit();
            return true;

        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }

            throw exception;

        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException exception) {
                throw new SQLException(
                        "No se pudo restaurar el modo de transacción de SQLite.",
                        exception
                );
            }
        }
    }

    public boolean reservePurchase(long id) throws SQLException {
        String sql = """
            UPDATE auction_listings
            SET status = ?
            WHERE id = ? AND status = ?
            """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, Auction.Status.PURCHASE_PENDING.name());
            statement.setLong(2, id);
            statement.setString(3, Auction.Status.ACTIVE.name());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean releasePurchase(long id) throws SQLException {
        String sql = """
            UPDATE auction_listings
            SET status = ?
            WHERE id = ? AND status = ?
            """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, Auction.Status.ACTIVE.name());
            statement.setLong(2, id);
            statement.setString(3, Auction.Status.PURCHASE_PENDING.name());

            return statement.executeUpdate() == 1;
        }
    }

    public boolean completePurchase(long id) throws SQLException {
        String sql = """
            UPDATE auction_listings
            SET status = ?
            WHERE id = ? AND status = ?
            """;

        try (PreparedStatement statement =
                     databaseManager.getConnection().prepareStatement(sql)) {

            statement.setString(1, Auction.Status.SOLD.name());
            statement.setLong(2, id);
            statement.setString(3, Auction.Status.PURCHASE_PENDING.name());

            return statement.executeUpdate() == 1;
        }
    }

    public int expireDueAuctions(long currentTimeMillis)
            throws SQLException {

        if (currentTimeMillis < 0) {
            throw new IllegalArgumentException(
                    "El tiempo actual no puede ser negativo."
            );
        }

        Connection connection = databaseManager.getConnection();
        boolean originalAutoCommit = connection.getAutoCommit();
        int expiredCount = 0;

        try {
            connection.setAutoCommit(false);

            List<ExpiredListing> expiredListings = new ArrayList<>();

            String selectSql = """
                SELECT id, seller_uuid, item_data
                FROM auction_listings
                WHERE status = ?
                  AND expires_at <= ?
                ORDER BY id ASC
                """;

            try (PreparedStatement statement =
                         connection.prepareStatement(selectSql)) {

                statement.setString(1, Auction.Status.ACTIVE.name());
                statement.setLong(2, currentTimeMillis);

                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        long id = result.getLong("id");
                        String sellerUUID = result.getString("seller_uuid");
                        byte[] itemData = result.getBytes("item_data");

                        if (sellerUUID == null
                                || itemData == null
                                || itemData.length == 0) {
                            throw new SQLException(
                                    "Datos inválidos en la publicación vencida #"
                                            + id
                            );
                        }

                        try {
                            UUID.fromString(sellerUUID);
                        } catch (IllegalArgumentException exception) {
                            throw new SQLException(
                                    "UUID inválido en la publicación vencida #"
                                            + id,
                                    exception
                            );
                        }

                        expiredListings.add(
                                new ExpiredListing(
                                        id,
                                        sellerUUID,
                                        itemData
                                )
                        );
                    }
                }
            }

            String insertSql = """
                INSERT INTO pending_items (
                    owner_uuid,
                    item_data,
                    reason,
                    created_at
                ) VALUES (?, ?, ?, ?)
                """;

            String updateSql = """
                UPDATE auction_listings
                SET status = ?
                WHERE id = ?
                  AND status = ?
                  AND expires_at <= ?
                """;

            for (ExpiredListing listing : expiredListings) {

                try (PreparedStatement insert =
                             connection.prepareStatement(insertSql)) {

                    insert.setString(1, listing.sellerUUID);
                    insert.setBytes(2, listing.itemData);
                    insert.setString(
                            3,
                            "EXPIRED_LISTING:" + listing.id
                    );
                    insert.setLong(4, currentTimeMillis);

                    if (insert.executeUpdate() != 1) {
                        throw new SQLException(
                                "No se pudo guardar en Storage el objeto "
                                        + "de la publicación #" + listing.id
                        );
                    }
                }

                try (PreparedStatement update =
                             connection.prepareStatement(updateSql)) {

                    update.setString(1, Auction.Status.EXPIRED.name());
                    update.setLong(2, listing.id);
                    update.setString(3, Auction.Status.ACTIVE.name());
                    update.setLong(4, currentTimeMillis);

                    if (update.executeUpdate() != 1) {
                        throw new SQLException(
                                "No se pudo marcar como vencida la publicación #"
                                        + listing.id
                        );
                    }
                }

                expiredCount++;
            }

            connection.commit();
            return expiredCount;

        } catch (SQLException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                exception.addSuppressed(rollbackException);
            }

            throw exception;

        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static final class ExpiredListing {

        private final long id;
        private final String sellerUUID;
        private final byte[] itemData;

        private ExpiredListing(
                long id,
                String sellerUUID,
                byte[] itemData
        ) {
            this.id = id;
            this.sellerUUID = sellerUUID;
            this.itemData = itemData.clone();
        }
    }
}

