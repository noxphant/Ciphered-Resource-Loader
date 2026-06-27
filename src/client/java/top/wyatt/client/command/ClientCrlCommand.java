package top.wyatt.client.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import top.wyatt.client.screen.CrlPackManagerScreen;
import top.wyatt.client.screen.CrlSettingsScreen;
import top.wyatt.client.screen.PackDownloadScreen;

public class ClientCrlCommand {

    /**
     * 待打开的屏幕信息。命令执行时设置，在 END_CLIENT_TICK 时消费。
     * 使用此机制确保在 ChatScreen.setScreen(null) 完成后再打开新屏幕。
     */
    private static volatile Screen pendingScreen;
    private static boolean tickHandlerRegistered = false;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // CipheredResourceLoaderClient.LOGGER.info("开始注册客户端CRL命令...");

            dispatcher.register(ClientCommandManager.literal("crl")
                    .executes(ClientCrlCommand::executeRoot)
                    .then(ClientCommandManager.literal("settings")
                            .executes(ClientCrlCommand::executeSettings))
                    .then(ClientCommandManager.literal("packs")
                            .executes(ClientCrlCommand::executePacks))
                    .then(ClientCommandManager.literal("download_packs")
                            .executes(ClientCrlCommand::executeDownloadPacks))
            );

            // CipheredResourceLoaderClient.LOGGER.info("客户端CRL命令注册完成");
        });

        // 注册 tick 事件处理器，用于在 ChatScreen 关闭后打开新屏幕
        if (!tickHandlerRegistered) {
            tickHandlerRegistered = true;
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                Screen screen = pendingScreen;
                if (screen != null) {
                    pendingScreen = null;
                    // CipheredResourceLoaderClient.LOGGER.info("END_CLIENT_TICK: 打开待处理的屏幕, currentScreen={}",
                    //         client.currentScreen != null ? client.currentScreen.getClass().getSimpleName() : "null");
                    client.setScreen(screen);
                    // CipheredResourceLoaderClient.LOGGER.info("END_CLIENT_TICK: 屏幕已设置, currentScreen={}",
                    //         client.currentScreen != null ? client.currentScreen.getClass().getSimpleName() : "null");
                }
            });
        }
    }

    private static int executeRoot(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        // CipheredResourceLoaderClient.LOGGER.info("执行 /crl 根命令");
        sendMessage("§e===== CRL 命令帮助 =====");
        sendMessage("§f/crl settings §7- 打开设置界面");
        sendMessage("§f/crl packs §7- 打开资源包管理界面");
        sendMessage("§f/crl download_packs §7- 打开资源包下载页面");
        return 1;
    }

    private static int executeSettings(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        // CipheredResourceLoaderClient.LOGGER.info("执行 /crl settings 命令");
        openSettingsScreen();
        return 1;
    }

    private static int executePacks(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        // CipheredResourceLoaderClient.LOGGER.info("执行 /crl packs 命令");
        openPacksScreen();
        return 1;
    }

    private static int executeDownloadPacks(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        final MinecraftClient client = MinecraftClient.getInstance();
        final Screen current = client.currentScreen;
        pendingScreen = new PackDownloadScreen(current);
        sendMessage("§a正在打开资源包下载页面...");
        return 1;
    }

    private static void openSettingsScreen() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final Screen current = client.currentScreen;
        // CipheredResourceLoaderClient.LOGGER.info("打开设置界面，父屏幕: {}",
        //         current != null ? current.getClass().getSimpleName() : "null");
        // 设置待处理屏幕，由 END_CLIENT_TICK 处理器在 ChatScreen 关闭后打开
        pendingScreen = new CrlSettingsScreen(current);
        sendMessage("§a正在打开 CRL 设置界面...");
    }

    private static void openPacksScreen() {
        final MinecraftClient client = MinecraftClient.getInstance();
        final Screen current = client.currentScreen;
        // CipheredResourceLoaderClient.LOGGER.info("打开资源包管理界面，父屏幕: {}",
        //         current != null ? current.getClass().getSimpleName() : "null");
        // 设置待处理屏幕，由 END_CLIENT_TICK 处理器在 ChatScreen 关闭后打开
        pendingScreen = new CrlPackManagerScreen(current);
        sendMessage("§a正在打开加密资源包管理界面...");
    }

    private static void sendMessage(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text), false);
        }
    }
}