package top.wyatt.server.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import top.wyatt.CipheredResourceLoader;
import top.wyatt.server.config.CrlConfig;
import top.wyatt.server.validation.RequiredPackValidator;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Collection;

public class CrlCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("crladmin")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(CrlCommand::executeHelp)
                .then(CommandManager.literal("reload")
                        .executes(CrlCommand::executeReload))
                .then(CommandManager.literal("mandatory_check")
                        .executes(CrlCommand::executeMandatoryCheckStatus)
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(CrlCommand::executeMandatoryCheck)))
                .then(CommandManager.literal("setport")
                        .executes(CrlCommand::executeSetPortStatus)
                        .then(CommandManager.argument("port", IntegerArgumentType.integer(1, 65535))
                                .executes(CrlCommand::executeSetPort)))
        );
    }

    private static int executeHelp(CommandContext<ServerCommandSource> context) {
        context.getSource().sendFeedback(() -> Text.translatable("ciphered-resource-loader.server.help.title"), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§f/crladmin reload §7- ").append(
                        Text.translatable("ciphered-resource-loader.server.help.reload")), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§f/crladmin mandatory_check §7- ").append(
                        Text.translatable("ciphered-resource-loader.server.help.mandatory")), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§f/crladmin mandatory_check [true/false] §7- ").append(
                        Text.translatable("ciphered-resource-loader.server.help.mandatory_set")), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§f/crladmin setport §7- ").append(
                        Text.translatable("ciphered-resource-loader.server.help.setport")), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§f/crladmin setport [端口号] §7- ").append(
                        Text.translatable("ciphered-resource-loader.server.help.setport_set")), false);
        context.getSource().sendFeedback(() ->
                Text.literal("§7").append(
                        Text.translatable("ciphered-resource-loader.server.help.port_note")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeMandatoryCheckStatus(CommandContext<ServerCommandSource> context) {
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        boolean enabled = config.isMandatoryCheck();
        String statusKey = enabled
                ? "ciphered-resource-loader.server.mandatory.enabled"
                : "ciphered-resource-loader.server.mandatory.disabled";
        context.getSource().sendFeedback(() ->
                Text.translatable("ciphered-resource-loader.server.mandatory.status",
                        Text.translatable(statusKey)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeMandatoryCheck(CommandContext<ServerCommandSource> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        RequiredPackValidator validator = CipheredResourceLoader.getPackValidator();

        config.setMandatoryCheck(enabled);

        String statusKey = enabled
                ? "ciphered-resource-loader.server.mandatory.enabled"
                : "ciphered-resource-loader.server.mandatory.disabled";
        context.getSource().sendFeedback(() ->
                Text.translatable("ciphered-resource-loader.server.mandatory.toggled",
                        Text.translatable(statusKey)), true);

        if (enabled) {
            validator.checkAllOnlinePlayers();
            context.getSource().sendFeedback(() ->
                    Text.translatable("ciphered-resource-loader.server.mandatory.check_all"), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeReload(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();

        CrlConfig crlConfig = CipheredResourceLoader.getCrlConfig();
        RequiredPackValidator validator = CipheredResourceLoader.getPackValidator();

        crlConfig.reload();
        validator.validateAllPlayers();

        source.sendFeedback(() ->
                Text.translatable("ciphered-resource-loader.server.reload_done"), true);

        Collection<ServerPlayerEntity> players = source.getServer().getPlayerManager().getPlayerList();
        for (ServerPlayerEntity player : players) {
            String statusMessage = validator.getPlayerStatusMessage(player);
            player.sendMessage(Text.literal(statusMessage), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * 查看当前传输端口配置状态。
     */
    private static int executeSetPortStatus(CommandContext<ServerCommandSource> context) {
        CrlConfig config = CipheredResourceLoader.getCrlConfig();
        int port = config.getTransferPort();
        if (port > 0) {
            context.getSource().sendFeedback(() ->
                    Text.translatable("ciphered-resource-loader.server.port.status_fixed", port), false);
        } else {
            context.getSource().sendFeedback(() ->
                    Text.translatable("ciphered-resource-loader.server.port.status_auto"), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * 设置固定传输端口（持久化到 crl_config.json）。
     * 不要求端口当前可用 —— 端口会在服务器重启时绑定。
     */
    private static int executeSetPort(CommandContext<ServerCommandSource> context) {
        int port = IntegerArgumentType.getInteger(context, "port");
        CrlConfig config = CipheredResourceLoader.getCrlConfig();

        // 合法性验证
        if (port < 1 || port > 65535) {
            context.getSource().sendFeedback(() ->
                    Text.translatable("ciphered-resource-loader.server.port.invalid", port), true);
            return 0;
        }

        // 检查端口是否已被占用
        try (ServerSocket probe = new ServerSocket(port)) {
            probe.setReuseAddress(true);
            // 端口可用
        } catch (IOException e) {
            context.getSource().sendFeedback(() ->
                    Text.translatable("ciphered-resource-loader.server.port.occupied", port), true);
            return 0;
        }

        config.setTransferPort(port);
        final int savedPort = port;
        context.getSource().sendFeedback(() ->
                Text.translatable("ciphered-resource-loader.server.port.set", savedPort), true);

        CipheredResourceLoader.LOGGER.info("管理员已将传输端口设置为 {}，已保存到 crl_config.json", savedPort);
        return Command.SINGLE_SUCCESS;
    }
}