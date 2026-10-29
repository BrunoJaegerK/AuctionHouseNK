package com.brunojaegerk.auctionhouse.auction;

import cn.nukkit.item.Item;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ItemSerializer {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_NBT_SIZE = 1_048_576;

    private ItemSerializer() {
    }

    public static byte[] serialize(Item item) throws IOException {
        if (item == null || item.isNull() || item.getCount() <= 0) {
            throw new IllegalArgumentException(
                    "No se puede serializar un objeto vacío."
            );
        }

        byte[] nbt = item.getCompoundTag();

        if (nbt == null) {
            nbt = new byte[0];
        }

        if (nbt.length > MAX_NBT_SIZE) {
            throw new IllegalArgumentException(
                    "Los datos NBT del objeto son demasiado grandes."
            );
        }

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(byteStream)) {
            output.writeInt(FORMAT_VERSION);
            output.writeInt(item.getId());
            output.writeInt(item.getDamage());
            output.writeInt(item.getCount());
            output.writeInt(nbt.length);
            output.write(nbt);
        }

        return byteStream.toByteArray();
    }

    public static Item deserialize(byte[] data) throws IOException {
        if (data == null || data.length < 20) {
            throw new IOException(
                    "Los datos serializados del objeto son inválidos."
            );
        }

        try (DataInputStream input = new DataInputStream(
                new ByteArrayInputStream(data))) {

            int version = input.readInt();

            if (version != FORMAT_VERSION) {
                throw new IOException(
                        "Versión de serialización no compatible: " + version
                );
            }

            int id = input.readInt();
            int damage = input.readInt();
            int count = input.readInt();
            int nbtLength = input.readInt();

            if (id <= 0 || count <= 0 || nbtLength < 0
                    || nbtLength > MAX_NBT_SIZE
                    || nbtLength != input.available()) {
                throw new IOException(
                        "Los datos del objeto no superan la validación."
                );
            }

            byte[] nbt = new byte[nbtLength];
            input.readFully(nbt);

            Item item = Item.get(id, damage, count, nbt);

            if (item == null || item.isNull()) {
                throw new IOException(
                        "Nukkit no pudo reconstruir el objeto."
                );
            }

            return item;
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Los datos del objeto no son válidos.",
                    exception
            );
        }
    }
}

