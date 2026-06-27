package top.wyatt.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import top.wyatt.client.CipheredResourceLoaderClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientUiConfig {
    private static final String CONFIG_FILE = "crl_client_ui_config.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ClientUiConfig instance;

    private boolean showPackScreenButton = true;
    private boolean showServerListIndicator = true;
    private boolean showDownloadProgress = true;
    private boolean showStatusMessages = true;

    private Path configPath;

    private ClientUiConfig() {}

    public static ClientUiConfig getInstance() {
        if (instance == null) {
            instance = new ClientUiConfig();
            instance.load();
        }
        return instance;
    }

    private void load() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        try {
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                ConfigData data = GSON.fromJson(content, ConfigData.class);
                if (data != null) {
                    this.showPackScreenButton = data.showPackScreenButton != null ? data.showPackScreenButton : true;
                    this.showServerListIndicator = data.showServerListIndicator != null ? data.showServerListIndicator : true;
                    this.showDownloadProgress = data.showDownloadProgress != null ? data.showDownloadProgress : true;
                    this.showStatusMessages = data.showStatusMessages != null ? data.showStatusMessages : true;
                }
                CipheredResourceLoaderClient.LOGGER.debug("客户端UI配置加载成功");
            } else {
                save();
                CipheredResourceLoaderClient.LOGGER.debug("已生成默认客户端UI配置");
            }
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("加载客户端UI配置失败，使用默认配置", e);
        }
    }

    public void save() {
        try {
            ConfigData data = new ConfigData();
            data.showPackScreenButton = this.showPackScreenButton;
            data.showServerListIndicator = this.showServerListIndicator;
            data.showDownloadProgress = this.showDownloadProgress;
            data.showStatusMessages = this.showStatusMessages;

            Path configDir = FabricLoader.getInstance().getConfigDir();
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }
            Files.writeString(configPath, GSON.toJson(data));
            CipheredResourceLoaderClient.LOGGER.debug("客户端UI配置已保存");
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("保存客户端UI配置失败", e);
        }
    }

    public boolean isShowPackScreenButton() {
        return showPackScreenButton;
    }

    public void setShowPackScreenButton(boolean showPackScreenButton) {
        this.showPackScreenButton = showPackScreenButton;
    }

    public boolean isShowServerListIndicator() {
        return showServerListIndicator;
    }

    public void setShowServerListIndicator(boolean showServerListIndicator) {
        this.showServerListIndicator = showServerListIndicator;
    }

    public boolean isShowDownloadProgress() {
        return showDownloadProgress;
    }

    public void setShowDownloadProgress(boolean showDownloadProgress) {
        this.showDownloadProgress = showDownloadProgress;
    }

    public boolean isShowStatusMessages() {
        return showStatusMessages;
    }

    public void setShowStatusMessages(boolean showStatusMessages) {
        this.showStatusMessages = showStatusMessages;
    }

    private static class ConfigData {
        Boolean showPackScreenButton;
        Boolean showServerListIndicator;
        Boolean showDownloadProgress;
        Boolean showStatusMessages;
    }
}
