package top.wyatt.server.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.core.pack.ServerPackEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ServerPackConfig {
    private static final String CONFIG_DIR = "ciphered-resource-loader";
    private static final String CONFIG_FILE = "packs.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private List<ServerPackEntry> packList = new ArrayList<>();
    private Path configFilePath;

    public void loadOrCreateDefault() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_DIR);
        configFilePath = configDir.resolve(CONFIG_FILE);

        try {
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.notExists(configFilePath)) {
                generateDefaultConfig();
                CipheredResourceLoader.LOGGER.info("已生成默认资源包配置文件: {}", CONFIG_FILE);
            }

            loadConfig();
        } catch (IOException e) {
            CipheredResourceLoader.LOGGER.error("资源包配置文件加载失败", e);
            packList = new ArrayList<>();
        }
    }

    public void reload() {
        loadConfig();
        CipheredResourceLoader.LOGGER.info("资源包配置已热重载，当前共 {} 个资源包", packList.size());
    }

    private void loadConfig() {
        try {
            String content = Files.readString(configFilePath);
            packList = GSON.fromJson(content, new TypeToken<List<ServerPackEntry>>() {}.getType());
            if (packList == null) {
                packList = new ArrayList<>();
            }
        } catch (Exception e) {
            CipheredResourceLoader.LOGGER.error("配置文件解析失败，请检查JSON格式", e);
            packList = new ArrayList<>();
        }
    }

    private void generateDefaultConfig() throws IOException {
        List<ServerPackEntry> defaultList = new ArrayList<>();
        String defaultJson = GSON.toJson(defaultList);
        Files.writeString(configFilePath, defaultJson);
    }

    public List<ServerPackEntry> getAllPacks() {
        return Collections.unmodifiableList(packList);
    }

    public List<ServerPackEntry> getRequiredPacks() {
        return packList.stream()
                .filter(entry -> entry.required)
                .collect(Collectors.toUnmodifiableList());
    }

    public int getPackCount() {
        return packList.size();
    }
}