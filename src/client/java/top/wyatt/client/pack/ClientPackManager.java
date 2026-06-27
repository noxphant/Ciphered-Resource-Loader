package top.wyatt.client.pack;

import net.fabricmc.loader.api.FabricLoader;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.core.crypto.HashUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ClientPackManager {
    private static final String ENCRYPTED_PACKS_DIR = "encrypted_resourcepacks";
    private static final String DEFAULT_RESOURCEPACKS_DIR = "resourcepacks";

    private final Path encryptedPacksDir;
    private final Path defaultResourcepacksDir;
    private final Map<String, PackState> packStates = new ConcurrentHashMap<>();
    private final Map<String, DownloadTask> downloadTasks = new ConcurrentHashMap<>();
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(3);

    public ClientPackManager() {
        FabricLoader loader = FabricLoader.getInstance();
        this.encryptedPacksDir = loader.getGameDir().resolve(ENCRYPTED_PACKS_DIR);
        this.defaultResourcepacksDir = loader.getGameDir().resolve(DEFAULT_RESOURCEPACKS_DIR);

        try {
            if (Files.notExists(encryptedPacksDir)) {
                Files.createDirectories(encryptedPacksDir);
                CipheredResourceLoaderClient.LOGGER.info("创建加密资源包目录: {}", encryptedPacksDir);
            }
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("创建加密资源包目录失败", e);
        }
    }

    public void scanLocalEncryptedPacks() {
        packStates.clear();
        try (var stream = Files.list(encryptedPacksDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".crp"))
                    .forEach(packPath -> {
                        String packId = packPath.getFileName().toString().replace(".crp", "");
                        packStates.put(packId, new PackState(packId, packPath, true));
                        CipheredResourceLoaderClient.LOGGER.debug("发现本地加密资源包: {}", packId);
                    });
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("扫描本地加密资源包失败", e);
        }
    }

    public void downloadPack(String packId, String fileName, String url, long fileSize, String expectedHash,
                             boolean encrypted, Consumer<DownloadProgress> progressCallback) {
        if (downloadTasks.containsKey(packId)) {
            CipheredResourceLoaderClient.LOGGER.warn("资源包 {} 正在下载中", packId);
            return;
        }

        DownloadTask task = new DownloadTask(packId, fileName, url, fileSize, expectedHash, encrypted, progressCallback);
        downloadTasks.put(packId, task);
        downloadExecutor.submit(task);
    }

    public boolean isPackDownloaded(String packId, String expectedHash) {
        PackState state = packStates.get(packId);
        if (state == null || !state.downloaded) return false;

        try {
            String actualHash = HashUtil.sha256Hex(state.filePath);
            return actualHash.equalsIgnoreCase(expectedHash);
        } catch (IOException e) {
            return false;
        }
    }

    public Path getPackPath(String packId) {
        PackState state = packStates.get(packId);
        return state != null ? state.filePath : null;
    }

    public void clearServerPackStates() {
        packStates.clear();
        downloadTasks.clear();
    }

    public Path getDownloadPath(String fileName, boolean encrypted) {
        if (encrypted) {
            return encryptedPacksDir.resolve(fileName);
        } else {
            return defaultResourcepacksDir.resolve(fileName);
        }
    }

    public void markPackDownloaded(String packId, Path filePath) {
        packStates.put(packId, new PackState(packId, filePath, true));
    }

    public Path getEncryptedPacksDir() {
        return encryptedPacksDir;
    }

    private class DownloadTask implements Runnable {
        private final String packId;
        private final String fileName;
        private final String url;
        private final long fileSize;
        private final String expectedHash;
        private final boolean encrypted;
        private final Consumer<DownloadProgress> progressCallback;

        DownloadTask(String packId, String fileName, String url, long fileSize, String expectedHash,
                     boolean encrypted, Consumer<DownloadProgress> progressCallback) {
            this.packId = packId;
            this.fileName = fileName;
            this.url = url;
            this.fileSize = fileSize;
            this.expectedHash = expectedHash;
            this.encrypted = encrypted;
            this.progressCallback = progressCallback;
        }

        @Override
        public void run() {
            Path savePath = getDownloadPath(fileName, encrypted);
            try {
                URL downloadUrl = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                try (InputStream is = conn.getInputStream();
                     OutputStream os = Files.newOutputStream(savePath)) {

                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int bytesRead;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                        downloaded += bytesRead;

                        if (progressCallback != null) {
                            int percent = fileSize > 0 ? (int) ((downloaded * 100) / fileSize) : -1;
                            progressCallback.accept(new DownloadProgress(packId, downloaded, fileSize, percent, false));
                        }
                    }

                    String actualHash = HashUtil.sha256Hex(savePath);
                    if (!actualHash.equalsIgnoreCase(expectedHash)) {
                        Files.deleteIfExists(savePath);
                        throw new IOException("哈希校验失败");
                    }

                    packStates.put(packId, new PackState(packId, savePath, true));
                    if (progressCallback != null) {
                        progressCallback.accept(new DownloadProgress(packId, downloaded, fileSize, 100, true));
                    }
                    CipheredResourceLoaderClient.LOGGER.info("资源包 {} 下载完成", packId);

                } finally {
                    conn.disconnect();
                }
            } catch (Exception e) {
                try {
                    Files.deleteIfExists(savePath);
                } catch (IOException ignored) {
                }
                if (progressCallback != null) {
                    progressCallback.accept(new DownloadProgress(packId, 0, fileSize, -1, false));
                }
                CipheredResourceLoaderClient.LOGGER.error("资源包 {} 下载失败", packId, e);
            } finally {
                downloadTasks.remove(packId);
            }
        }
    }

    public static class PackState {
        public final String packId;
        public final Path filePath;
        public final boolean downloaded;

        PackState(String packId, Path filePath, boolean downloaded) {
            this.packId = packId;
            this.filePath = filePath;
            this.downloaded = downloaded;
        }
    }

    public static class DownloadProgress {
        public final String packId;
        public final long downloaded;
        public final long total;
        public final int percent;
        public final boolean completed;

        public DownloadProgress(String packId, long downloaded, long total, int percent, boolean completed) {
            this.packId = packId;
            this.downloaded = downloaded;
            this.total = total;
            this.percent = percent;
            this.completed = completed;
        }
    }
}