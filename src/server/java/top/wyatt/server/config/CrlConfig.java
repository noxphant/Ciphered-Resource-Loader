package top.wyatt.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.core.crypto.HashUtil;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CrlConfig {
    private static final String CRL_PACKS_DIR = "CRL_packs";
    private static final String CONFIG_FILE = "crl_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Path crlPacksDir;
    private Path configFilePath;
    private double rateLimitMbPerSec = 10.0;
    private boolean mandatoryCheck = false;
    private int transferPort = 0;  // 0 = 自动分配，>0 = 固定端口
    private List<CrlPackEntry> packs = new ArrayList<>();

    public void loadOrCreateDefault() {
        crlPacksDir = FabricLoader.getInstance().getGameDir().resolve(CRL_PACKS_DIR);
        configFilePath = crlPacksDir.resolve(CONFIG_FILE);

        try {
            if (Files.notExists(crlPacksDir)) {
                Files.createDirectories(crlPacksDir);
                CipheredResourceLoader.LOGGER.info("已创建 CRL_packs 目录: {}", crlPacksDir);
            }

            if (Files.notExists(configFilePath)) {
                generateDefaultConfig();
                CipheredResourceLoader.LOGGER.info("已生成默认配置文件: {}", CONFIG_FILE);
            }

            loadConfig();
            autoUpdateHashes();
            CipheredResourceLoader.LOGGER.info("配置加载完成，共 {} 个资源包", packs.size());
        } catch (IOException e) {
            CipheredResourceLoader.LOGGER.error("配置加载失败", e);
            packs = new ArrayList<>();
        }
    }

    public void reload() {
        try {
            loadConfig();
            autoUpdateHashes();
            CipheredResourceLoader.LOGGER.info("配置已热重载，共 {} 个资源包", packs.size());
        } catch (IOException e) {
            CipheredResourceLoader.LOGGER.error("配置重载失败", e);
        }
    }

    private void loadConfig() throws IOException {
        String content = Files.readString(configFilePath);
        CrlConfigData data = GSON.fromJson(content, CrlConfigData.class);
        if (data != null) {
            this.rateLimitMbPerSec = data.rateLimitMbPerSec != null ? data.rateLimitMbPerSec : 10.0;
            this.mandatoryCheck = data.mandatoryCheck != null ? data.mandatoryCheck : false;
            this.transferPort = data.transferPort != null ? data.transferPort : 0;
            this.packs = data.packs != null ? data.packs : new ArrayList<>();
        }
    }

    private void generateDefaultConfig() throws IOException {
        CrlConfigData data = new CrlConfigData();
        data.rateLimitMbPerSec = 10.0;
        data.mandatoryCheck = false;
        data.packs = new ArrayList<>();
        Files.writeString(configFilePath, GSON.toJson(data));
    }

    private void autoUpdateHashes() throws IOException {
        boolean changed = false;
        for (CrlPackEntry pack : packs) {
            Path packFile = findPackFile(crlPacksDir, pack.getFileName());
            if (packFile == null) {
                CipheredResourceLoader.LOGGER.warn("未找到资源包文件: {} (目录: {})", pack.getFileName(), crlPacksDir);
                continue;
            }

            String currentHash = HashUtil.sha256Hex(packFile);
            long currentSize = Files.size(packFile);

            // 如果实际文件名与配置不同，自动更新
            String actualFileName = packFile.getFileName().toString();
            if (!actualFileName.equals(pack.getFileName())) {
                CipheredResourceLoader.LOGGER.info("自动修正文件名: {} -> {}", pack.getFileName(), actualFileName);
                pack.setFileName(actualFileName);
                changed = true;
            }

            if (!currentHash.equals(pack.getSha256()) || currentSize != pack.getFileSize()) {
                pack.setSha256(currentHash);
                pack.setFileSize(currentSize);
                changed = true;
                CipheredResourceLoader.LOGGER.debug("更新资源包哈希: {}", actualFileName);
            }
        }

        if (changed) {
            saveConfig();
        }
    }

    /**
     * 模糊查找资源包文件。
     * 1. 精确匹配
     * 2. 剥离 § 格式化码后匹配
     * 3. 检查配置中的 fileName 是否为目录中某个文件名的子串
     */
    public static Path findPackFile(Path dir, String expectedFileName) {
        if (expectedFileName == null || expectedFileName.isEmpty()) {
            return null;
        }

        // 1. 精确匹配
        Path exact = dir.resolve(expectedFileName);
        if (Files.exists(exact)) {
            return exact;
        }

        // 剥离 § 格式化码
        String strippedExpected = stripMinecraftFormatting(expectedFileName);

        try (Stream<Path> files = Files.list(dir)) {
            // 2. 剥离格式化码后匹配
            Optional<Path> fuzzy = files.filter(Files::isRegularFile)
                    .filter(f -> {
                        String actualName = f.getFileName().toString();
                        return stripMinecraftFormatting(actualName).equalsIgnoreCase(strippedExpected);
                    })
                    .findFirst();
            if (fuzzy.isPresent()) {
                return fuzzy.get();
            }
        } catch (IOException ignored) {
        }

        // 3. 配置中的 fileName 是实际文件名的子串（处理 !! 前缀等情况）
        try (Stream<Path> files = Files.list(dir)) {
            Optional<Path> fuzzy = files.filter(Files::isRegularFile)
                    .filter(f -> {
                        String actualName = f.getFileName().toString();
                        return actualName.contains(expectedFileName)
                                || expectedFileName.contains(actualName)
                                || stripMinecraftFormatting(actualName).contains(strippedExpected)
                                || strippedExpected.contains(stripMinecraftFormatting(actualName));
                    })
                    .findFirst();
            if (fuzzy.isPresent()) {
                return fuzzy.get();
            }
        } catch (IOException ignored) {
        }

        return null;
    }

    /**
     * 剥离 Minecraft 格式化码（§ + 单个字符）。
     */
    public static String stripMinecraftFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fklmnor]", "");
    }

    public void saveConfig() {
        try {
            CrlConfigData data = new CrlConfigData();
            data.rateLimitMbPerSec = this.rateLimitMbPerSec;
            data.mandatoryCheck = this.mandatoryCheck;
            data.transferPort = this.transferPort > 0 ? this.transferPort : null;
            data.packs = this.packs;
            Files.writeString(configFilePath, GSON.toJson(data));
        } catch (IOException e) {
            CipheredResourceLoader.LOGGER.error("保存配置失败", e);
        }
    }

    public boolean isMandatoryCheck() {
        return mandatoryCheck;
    }

    public void setMandatoryCheck(boolean mandatoryCheck) {
        this.mandatoryCheck = mandatoryCheck;
        saveConfig();
    }

    public double getRateLimitMbPerSec() { return rateLimitMbPerSec; }
    public void setRateLimitMbPerSec(double rateLimitMbPerSec) {
        this.rateLimitMbPerSec = rateLimitMbPerSec;
        saveConfig();
    }

    public int getTransferPort() { return transferPort; }

    public void setTransferPort(int transferPort) {
        this.transferPort = transferPort;
        saveConfig();
    }

    public List<CrlPackEntry> getAllPacks() { return Collections.unmodifiableList(packs); }

    public List<CrlPackEntry> getRequiredPacks() {
        return packs.stream().filter(CrlPackEntry::isRequired).collect(Collectors.toUnmodifiableList());
    }

    public List<CrlPackEntry> getEncryptedPacks() {
        return packs.stream().filter(CrlPackEntry::isEncrypted).collect(Collectors.toUnmodifiableList());
    }

    public int getPackCount() { return packs.size(); }

    public int getRequiredCount() {
        return (int) packs.stream().filter(CrlPackEntry::isRequired).count();
    }

    public int getOptionalCount() {
        return (int) packs.stream().filter(p -> !p.isRequired()).count();
    }

    public CrlPackEntry getPackById(String packId) {
        return packs.stream().filter(p -> p.getPackId().equals(packId)).findFirst().orElse(null);
    }

    public CrlPackEntry getPackByFileName(String fileName) {
        return packs.stream().filter(p -> p.getFileName().equals(fileName)).findFirst().orElse(null);
    }

    public Path getCrlPacksDir() { return crlPacksDir; }

    private static class CrlConfigData {
        public Double rateLimitMbPerSec;
        public Boolean mandatoryCheck;
        public Integer transferPort;
        public List<CrlPackEntry> packs;
    }
}