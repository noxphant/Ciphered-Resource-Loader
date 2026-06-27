package top.wyatt.client.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.wyatt.client.CipheredResourceLoaderClient;
import top.wyatt.core.ModConstants;
import top.wyatt.core.crypto.HashUtil;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 客户端文件下载器 —— 通过 TCP 从服务端接收文件并校验 SHA-256 哈希。
 * 每个下载任务提交到独立线程执行。
 */
public class FileDownloadClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModConstants.MOD_ID + "-transfer");
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 65536;

    private static final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CRL-Download-" + System.currentTimeMillis() % 100000);
        t.setDaemon(true);
        return t;
    });

    /**
     * 异步下载文件。返回 CompletableFuture，完成后携带下载结果。
     *
     * @param serverAddress 服务器地址（主机名或IP）
     * @param port          传输端口
     * @param packId        资源包ID
     * @param fileName      文件名
     * @param expectedHash  期望的 SHA-256 哈希
     * @param fileSize      期望的文件大小
     * @param savePath      保存路径
     * @param callback      进度回调（可选）
     * @return CompletableFuture<DownloadResult>
     */
    public static CompletableFuture<DownloadResult> downloadAsync(
            String serverAddress, int port, String packId, String fileName,
            String expectedHash, long fileSize, Path savePath,
            DownloadProgressCallback callback) {

        return CompletableFuture.supplyAsync(() -> {
            return download(serverAddress, port, packId, fileName,
                    expectedHash, fileSize, savePath, callback);
        }, executor);
    }

    private static DownloadResult download(
            String serverAddress, int port, String packId, String fileName,
            String expectedHash, long fileSize, Path savePath,
            DownloadProgressCallback callback) {

        LOGGER.info("[{}] 开始下载，目标: {}:{}，文件: {}", packId, serverAddress, port, fileName);

        Path tempFile = null;
        long actualFileSize = 0;
        try {
            // 下载到临时文件
            tempFile = savePath.resolveSibling(fileName + ".tmp");

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(serverAddress, port), CONNECT_TIMEOUT_MS);
                socket.setSoTimeout(READ_TIMEOUT_MS);

                try (InputStream in = new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE);
                     OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(tempFile), BUFFER_SIZE)) {

                    DataInputStream dataIn = new DataInputStream(in);

                    // 读取文件大小
                    actualFileSize = dataIn.readLong();

                    // 检查错误标记
                    if (actualFileSize == -1) {
                        int errorLen = dataIn.readInt();
                        byte[] errorBytes = new byte[errorLen];
                        dataIn.readFully(errorBytes);
                        String errorCode = new String(errorBytes, java.nio.charset.StandardCharsets.UTF_8);
                        throw new IOException("服务端错误: " + errorCode);
                    }

                    LOGGER.debug("[{}] 服务端返回文件大小: {} bytes", packId, actualFileSize);

                    // 接收文件数据
                    byte[] buffer = new byte[BUFFER_SIZE];
                    long totalReceived = 0;
                    long remaining = actualFileSize;

                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int bytesRead = in.read(buffer, 0, toRead);
                        if (bytesRead == -1) {
                            throw new IOException("连接意外关闭，已接收 " + totalReceived + "/" + actualFileSize);
                        }
                        fileOut.write(buffer, 0, bytesRead);
                        totalReceived += bytesRead;
                        remaining -= bytesRead;

                        if (callback != null) {
                            int percent = actualFileSize > 0 ? (int) ((totalReceived * 100) / actualFileSize) : 0;
                            callback.onProgress(percent, totalReceived, actualFileSize);
                        }
                    }
                    fileOut.flush();

                    LOGGER.debug("[{}] 文件数据已接收完，共 {} bytes，开始接收哈希", packId, totalReceived);

                    // 读取服务端发送的哈希值（64字符 ASCII）
                    byte[] hashBytes = new byte[64];
                    int hashRead = 0;
                    while (hashRead < 64) {
                        int n = in.read(hashBytes, hashRead, 64 - hashRead);
                        if (n == -1) break;
                        hashRead += n;
                    }
                    String serverHash = new String(hashBytes, 0, hashRead, java.nio.charset.StandardCharsets.US_ASCII).trim();

                    LOGGER.debug("[{}] 服务端哈希: {}", packId, serverHash);
                }
            }

            // 校验本地文件哈希
            String localHash = HashUtil.sha256Hex(tempFile);
            LOGGER.debug("[{}] 本地文件哈希: {}", packId, localHash);

            if (!localHash.equalsIgnoreCase(expectedHash)) {
                LOGGER.error("[{}] 哈希校验失败！期望: {}，本地: {}", packId, expectedHash, localHash);
                Files.deleteIfExists(tempFile);
                return DownloadResult.failure(packId, "哈希校验失败: 期望 " + expectedHash + "，实际 " + localHash);
            }

            // 哈希校验通过，移动到目标路径
            Files.move(tempFile, savePath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[{}] 下载完成，哈希校验通过，已保存至 {}", packId, savePath);

            return DownloadResult.success(packId, localHash, actualFileSize);

        } catch (IOException e) {
            LOGGER.error("[{}] 下载失败", packId, e);
            if (tempFile != null) {
                try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
            }
            return DownloadResult.failure(packId, e.getMessage());
        }
    }

    /**
     * 下载进度回调接口。
     */
    @FunctionalInterface
    public interface DownloadProgressCallback {
        void onProgress(int percent, long downloaded, long total);
    }

    /**
     * 下载结果。
     */
    public static class DownloadResult {
        private final String packId;
        private final boolean success;
        private final String hash;
        private final long fileSize;
        private final String errorMessage;

        private DownloadResult(String packId, boolean success, String hash, long fileSize, String errorMessage) {
            this.packId = packId;
            this.success = success;
            this.hash = hash;
            this.fileSize = fileSize;
            this.errorMessage = errorMessage;
        }

        public static DownloadResult success(String packId, String hash, long fileSize) {
            return new DownloadResult(packId, true, hash, fileSize, null);
        }

        public static DownloadResult failure(String packId, String errorMessage) {
            return new DownloadResult(packId, false, null, 0, errorMessage);
        }

        public String packId() { return packId; }
        public boolean success() { return success; }
        public String hash() { return hash; }
        public long fileSize() { return fileSize; }
        public String errorMessage() { return errorMessage; }
    }
}