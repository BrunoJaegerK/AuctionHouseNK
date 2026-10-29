package com.brunojaegerk.auctionhouse.storage;

import cn.nukkit.plugin.PluginBase;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public final class DatabaseManager {

    private final PluginBase plugin;
    private Connection connection;

    public DatabaseManager(PluginBase plugin) {
        this.plugin = plugin;
    }

    public synchronized void initialize() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return;
        }

        File dataFolder = plugin.getDataFolder();

        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new SQLException(
                    "No se pudo crear la carpeta de datos de AuctionHouse."
            );
        }

        String databaseName = plugin.getConfig().getString(
                "storage.database",
                "auctionhouse.db"
        );

        // Evita que la configuración apunte fuera de la carpeta del plugin.
        if (databaseName == null
                || databaseName.isBlank()
                || !new File(databaseName).getName().equals(databaseName)
                || !databaseName.toLowerCase().endsWith(".db")) {
            throw new SQLException(
                    "Nombre de base de datos no válido en config.yml."
            );
        }

        File databaseFile = new File(dataFolder, databaseName);
        String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        connection = DriverManager.getConnection(url);

        try {
            createTables();
        } catch (SQLException exception) {
            close();
            throw exception;
        }
    }

    private void createTables() throws SQLException {
        String createListings = """
                CREATE TABLE IF NOT EXISTS auction_listings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller_uuid TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    price REAL NOT NULL CHECK (price > 0),
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'ACTIVE'
                )
                """;

        String createPendingItems = """
                CREATE TABLE IF NOT EXISTS pending_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    owner_uuid TEXT NOT NULL,
                    item_data BLOB NOT NULL,
                    reason TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """;

        String createSellerIndex = """
                CREATE INDEX IF NOT EXISTS idx_auction_seller
                ON auction_listings(seller_uuid, status)
                """;

        String createExpiryIndex = """
                CREATE INDEX IF NOT EXISTS idx_auction_expiry
                ON auction_listings(status, expires_at)
                """;

        try (Statement statement = connection.createStatement()) {
            statement.execute(createListings);
            statement.execute(createPendingItems);
            statement.execute(createSellerIndex);
            statement.execute(createExpiryIndex);
        }
    }

    public synchronized long savePendingItem(
            UUID ownerUUID,
            byte[] itemData,
            String reason
    ) throws SQLException {

        if (ownerUUID == null) {
            throw new IllegalArgumentException("El UUID del propietario es obligatorio.");
        }

        if (itemData == null || itemData.length == 0) {
            throw new IllegalArgumentException("Los datos del objeto están vacíos.");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio.");
        }

        String sql = """
            INSERT INTO pending_items (
                owner_uuid,
                item_data,
                reason,
                created_at
            ) VALUES (?, ?, ?, ?)
            """;

        try (var statement = getConnection().prepareStatement(
                sql,
                java.sql.Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, ownerUUID.toString());
            statement.setBytes(2, itemData);
            statement.setString(3, reason);
            statement.setLong(4, System.currentTimeMillis());

            int inserted = statement.executeUpdate();

            if (inserted != 1) {
                throw new SQLException("No se pudo guardar el objeto pendiente.");
            }

            try (var keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new SQLException("No se pudo obtener el ID del objeto pendiente.");
    }

    public synchronized List<PendingItem> getPendingItems(
            UUID ownerUUID
    ) throws SQLException {

        if (ownerUUID == null) {
            throw new IllegalArgumentException(
                    "El UUID del propietario es obligatorio."
            );
        }

        String sql = """
            SELECT id, owner_uuid, item_data, reason, created_at
            FROM pending_items
            WHERE owner_uuid = ?
            ORDER BY created_at ASC, id ASC
            """;

        List<PendingItem> pendingItems = new ArrayList<>();

        try (PreparedStatement statement =
                     getConnection().prepareStatement(sql)) {

            statement.setString(1, ownerUUID.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID storedUUID = UUID.fromString(
                            resultSet.getString("owner_uuid")
                    );

                    PendingItem pendingItem = new PendingItem(
                            resultSet.getLong("id"),
                            storedUUID,
                            resultSet.getBytes("item_data"),
                            resultSet.getString("reason"),
                            resultSet.getLong("created_at")
                    );

                    pendingItems.add(pendingItem);
                }
            }
        }

        return pendingItems;
    }

    public synchronized boolean deletePendingItem(
            long itemId,
            UUID ownerUUID
    ) throws SQLException {

        if (itemId <= 0) {
            throw new IllegalArgumentException(
                    "El ID del objeto debe ser positivo."
            );
        }

        if (ownerUUID == null) {
            throw new IllegalArgumentException(
                    "El UUID del propietario es obligatorio."
            );
        }

        String sql = """
            DELETE FROM pending_items
            WHERE id = ? AND owner_uuid = ?
            """;

        try (PreparedStatement statement =
                     getConnection().prepareStatement(sql)) {

            statement.setLong(1, itemId);
            statement.setString(2, ownerUUID.toString());

            return statement.executeUpdate() == 1;
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException(
                    "La base de datos de AuctionHouse no está inicializada."
            );
        }

        return connection;
    }

    public synchronized boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException exception) {
            return false;
        }
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }

        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException exception) {
            plugin.getLogger().error(
                    "No se pudo cerrar la base de datos de AuctionHouse.",
                    exception
            );
        } finally {
            connection = null;
        }
    }
}

