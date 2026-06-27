package top.wyatt.server.validation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.config.CrlPackEntry;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RequiredPackValidator {
    private static final long TIMEOUT_MS = 30 * 1000L;
    private static final long CHECK_INTERVAL_MS = 1000L;

    private final Map<UUID, ValidationState> playerStates = new ConcurrentHashMap<>();
    private long lastCheckTime = 0;
    private MinecraftServer currentServer;

    public RequiredPackValidator() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            currentServer = server;
            tickCheck();
        });
    }

    public void startPlayerValidation(ServerPlayerEntity player) {
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        playerStates.put(player.getUuid(), new ValidationState(System.currentTimeMillis()));
        
        if (config.getRequiredPacks().isEmpty()) {
            ValidationState state = playerStates.get(player.getUuid());
            if (state != null) {
                state.completed = true;
            }
            String statusMsg = getPlayerStatusMessage(player);
            player.sendMessage(Text.literal(statusMsg), false);
            return;
        }
        
        CipheredResourceLoader.LOGGER.debug(
                "开始校验玩家 {} 的必选资源包，超时时间: {}ms",
                player.getName().getString(), TIMEOUT_MS
        );
    }

    public void onPlayerPackReady(ServerPlayerEntity player, String packId) {
        ValidationState state = playerStates.get(player.getUuid());
        if (state == null) return;

        state.loadedPacks.add(packId);

        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        boolean allRequiredLoaded = config.getRequiredPacks().stream()
                .map(CrlPackEntry::getPackId)
                .allMatch(state.loadedPacks::contains);

        if (allRequiredLoaded) {
            state.completed = true;
            String statusMsg = getPlayerStatusMessage(player);
            player.sendMessage(Text.literal(statusMsg), false);
            CipheredResourceLoader.LOGGER.debug(
                    "玩家 {} 已完成所有必选资源包加载",
                    player.getName().getString()
            );
        }
    }

    public void clearPlayerState(ServerPlayerEntity player) {
        playerStates.remove(player.getUuid());
    }

    public void validateAllPlayers() {
        CipheredResourceLoader.getCrlConfig().getAllPacks();
        for (Map.Entry<UUID, ValidationState> entry : playerStates.entrySet()) {
            entry.getValue().completed = false;
        }
    }

    public String getPlayerStatusMessage(ServerPlayerEntity player) {
        ValidationState state = playerStates.get(player.getUuid());
        CrlConfig config = CipheredResourceLoader.getCrlConfig();

        int requiredTotal = config.getRequiredCount();
        int optionalTotal = config.getOptionalCount();

        int requiredLoaded = 0;
        if (state != null) {
            requiredLoaded = (int) config.getRequiredPacks().stream()
                    .map(CrlPackEntry::getPackId)
                    .filter(state.loadedPacks::contains)
                    .count();
        }

        int optionalLoaded = 0;
        if (state != null) {
            optionalLoaded = (int) config.getEncryptedPacks().stream()
                    .filter(p -> !p.isRequired())
                    .map(CrlPackEntry::getPackId)
                    .filter(state.loadedPacks::contains)
                    .count();
        }

        return String.format("必选资源包：%d/%d\n选装资源包：%d/%d",
                requiredLoaded, requiredTotal, optionalLoaded, optionalTotal);
    }

    private void tickCheck() {
        long now = System.currentTimeMillis();

        if (now - lastCheckTime < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        boolean mandatoryCheck = config.isMandatoryCheck();
        
        if (!mandatoryCheck || config.getRequiredPacks().isEmpty()) {
            return;
        }

        playerStates.entrySet().removeIf(entry -> {
            ValidationState state = entry.getValue();
            
            boolean allRequiredLoaded = config.getRequiredPacks().stream()
                    .map(CrlPackEntry::getPackId)
                    .allMatch(state.loadedPacks::contains);
                    
            if (allRequiredLoaded) return true;

            if (mandatoryCheck && now - state.startTime > TIMEOUT_MS) {
                MinecraftServer server = currentServer;
                if (server != null) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        String kickMessage = "§c您未安装全部必选资源包，请安装后重新连接！\n\n" +
                                "§e必选资源包列表：\n";
                        for (CrlPackEntry pack : config.getRequiredPacks()) {
                            String packName = pack.getDisplayName() != null && !pack.getDisplayName().isEmpty()
                                    ? pack.getDisplayName() : pack.getPackId();
                            kickMessage += "§f - " + packName + "\n";
                        }
                        final String finalKickMessage = kickMessage;
                        server.execute(() -> player.networkHandler.disconnect(Text.literal(finalKickMessage)));
                        CipheredResourceLoader.LOGGER.info(
                                "玩家 {} 因未安装必选资源包被踢出",
                                player.getName().getString()
                        );
                    }
                }
                return true;
            }
            return false;
        });
    }

    public void checkAllOnlinePlayers() {
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        if (!config.isMandatoryCheck()) {
            CipheredResourceLoader.LOGGER.debug("必选资源包检查功能未启用，跳过检查");
            return;
        }

        if (config.getRequiredPacks().isEmpty()) {
            CipheredResourceLoader.LOGGER.debug("没有配置必选资源包，跳过检查");
            return;
        }

        MinecraftServer server = currentServer;
        if (server == null) return;

        CipheredResourceLoader.LOGGER.info("开始检查所有在线玩家的必选资源包安装状态");

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ValidationState state = playerStates.get(player.getUuid());
            boolean allRequiredLoaded = state != null && config.getRequiredPacks().stream()
                    .map(CrlPackEntry::getPackId)
                    .allMatch(state.loadedPacks::contains);

            if (!allRequiredLoaded) {
                String kickMessage = "§c您未安装全部必选资源包，请安装后重新连接！\n\n" +
                        "§e必选资源包列表：\n";
                for (CrlPackEntry pack : config.getRequiredPacks()) {
                    String packName = pack.getDisplayName() != null && !pack.getDisplayName().isEmpty()
                            ? pack.getDisplayName() : pack.getPackId();
                    kickMessage += "§f - " + packName + "\n";
                }
                final String finalKickMessage = kickMessage;
                server.execute(() -> player.networkHandler.disconnect(Text.literal(finalKickMessage)));
                CipheredResourceLoader.LOGGER.info(
                        "玩家 {} 因未安装必选资源包被踢出",
                        player.getName().getString()
                );
            }
        }
    }

    private static class ValidationState {
        long startTime;
        boolean completed = false;
        Set<String> loadedPacks = new HashSet<>();

        ValidationState(long startTime) {
            this.startTime = startTime;
        }
    }
}
