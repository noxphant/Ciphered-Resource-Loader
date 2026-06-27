package top.wyatt.client.pack;

import net.minecraft.resource.*;
import net.minecraft.resource.metadata.ResourceMetadataReader;
import net.minecraft.util.Identifier;
import top.wyatt.core.crypto.AesGcmCipher;
import top.wyatt.core.crypto.KeyDerivation;
import top.wyatt.core.pack.CipheredPackReader;
import top.wyatt.core.pack.index.IndexEntry;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class CipheredResourcePack implements ResourcePack {
    private final Path packPath;
    private final String packId;
    private final String displayName;
    private final byte[] masterKey;
    private CipheredPackReader reader;
    private boolean opened = false;
    private final Set<String> namespaces = new HashSet<>();

    public CipheredResourcePack(Path packPath, String packId, String displayName, byte[] masterKey) {
        this.packPath = packPath;
        this.packId = packId;
        this.displayName = displayName;
        this.masterKey = masterKey;
    }

    private void ensureOpened() {
        if (!opened) {
            try {
                reader = new CipheredPackReader(packPath.toFile());
                reader.openAndParseHeader();
                reader.loadIndex(masterKey);
                opened = true;
            } catch (Exception e) {
                throw new RuntimeException("打开加密资源包失败: " + packPath, e);
            }
        }
    }

    @Override
    public String getName() {
        return displayName;
    }

    @Override
    public InputSupplier<InputStream> openRoot(String[] segments) {
        return null;
    }

    @Override
    public InputSupplier<InputStream> open(ResourceType type, Identifier id) {
        ensureOpened();
        IndexEntry entry = reader.getResourceEntry(id.toString());
        if (entry == null) {
            return null;
        }

        return () -> {
            try {
                byte[] encrypted = reader.readBlock(entry.getBlockOffsets()[0], entry.getBlockLengths()[0]);
                byte[] blockKey = KeyDerivation.deriveKey(masterKey, reader.getGlobalSalt(), id.toString(), 32);
                byte[] plain = AesGcmCipher.decrypt(encrypted, blockKey);
                return new ByteArrayInputStream(plain);
            } catch (Exception e) {
                throw new IOException("解密资源失败: " + id, e);
            }
        };
    }

    @Override
    public void findResources(ResourceType type, String namespace, String prefix, ResultConsumer consumer) {
        ensureOpened();
        if (reader == null || !reader.isIndexLoaded()) return;
    }

    @Override
    public Set<String> getNamespaces(ResourceType type) {
        return namespaces;
    }

    @Override
    public void close() {
        if (reader != null) {
            reader.close();
        }
        opened = false;
    }

    @Override
    public <T> T parseMetadata(ResourceMetadataReader<T> metadataReader) throws IOException {
        return null;
    }

    public String getPackId() {
        return packId;
    }
}