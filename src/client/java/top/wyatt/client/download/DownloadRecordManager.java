package top.wyatt.client.download;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import top.wyatt.client.CipheredResourceLoaderClient;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DownloadRecordManager {
    private static final String RECORD_FILE = "crl_d_log.json";
    private static final int MAX_RECORDS = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type RECORD_LIST_TYPE = new TypeToken<List<DownloadRecord>>() {}.getType();

    private static DownloadRecordManager instance;

    private final List<DownloadRecord> records = new ArrayList<>();
    private Path recordFilePath;

    private DownloadRecordManager() {}

    public static DownloadRecordManager getInstance() {
        if (instance == null) {
            instance = new DownloadRecordManager();
            instance.load();
        }
        return instance;
    }

    private void load() {
        recordFilePath = FabricLoader.getInstance().getConfigDir().resolve(RECORD_FILE);
        try {
            if (Files.exists(recordFilePath)) {
                String content = Files.readString(recordFilePath);
                List<DownloadRecord> loaded = GSON.fromJson(content, RECORD_LIST_TYPE);
                if (loaded != null) {
                    records.addAll(loaded);
                }
                CipheredResourceLoaderClient.LOGGER.debug("加载下载记录成功，共 {} 条", records.size());
            } else {
                save();
                CipheredResourceLoaderClient.LOGGER.debug("创建新的下载记录文件");
            }
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("加载下载记录失败", e);
        }
    }

    private synchronized void save() {
        try {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            if (Files.notExists(configDir)) {
                Files.createDirectories(configDir);
            }
            Files.writeString(recordFilePath, GSON.toJson(records));
        } catch (IOException e) {
            CipheredResourceLoaderClient.LOGGER.error("保存下载记录失败", e);
        }
    }

    public synchronized void addRecord(DownloadRecord record) {
        records.add(0, record);
        while (records.size() > MAX_RECORDS) {
            records.remove(records.size() - 1);
        }
        save();
        CipheredResourceLoaderClient.LOGGER.debug("添加下载记录: {} - {}", record.getPackName(), record.isSuccess() ? "成功" : "失败");
    }

    public synchronized List<DownloadRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    public synchronized void clearRecords() {
        records.clear();
        save();
        CipheredResourceLoaderClient.LOGGER.info("已清除所有下载记录");
    }

    public synchronized void refresh() {
        records.clear();
        load();
    }

    public int getRecordCount() {
        return records.size();
    }
}
