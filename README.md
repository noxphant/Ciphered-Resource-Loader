# Ciphered Resource Loader

**加密资源包加载与服务端下发模组** — 支持可选/必选资源包、AES-GCM 内存解密防提取。

## 功能特性

- **加密资源包保护** — 使用 AES-GCM 加密资源包，客户端内存解密，防止直接提取资源文件
- **服务端下发** — 服务器管理员配置资源包后，客户端自动下载、解密、加载
- **必选 / 选装** — 支持必选（强制加载）和选装（玩家可选）两种资源包类型
- **独立传输通道** — 文件通过独立 TCP 端口传输，支持速率限制，不影响游戏网络
- **可视化 GUI** — 资源包管理界面、下载管理界面、设置界面，支持进度条与状态显示
- **多语言支持** — 内置中文 / 英文语言文件，支持 Minecraft 语言切换
- **安全机制** — 堆外内存池、会话级密钥管理、断开连接自动清理

## 环境要求

| 组件 | 版本 |
|------|------|
| Minecraft | 1.20.1 |
| Fabric Loader | >= 0.14.21 |
| Fabric API | 0.92.9+ |
| Java | >= 17 |

## 安装

### 服务端

1. 将模组 JAR 放入服务端 `mods/` 目录
2. 启动服务端，模组会在根目录生成 `CRL_packs/` 文件夹
3. 将加密资源包放入 `CRL_packs/` 目录
4. 编辑 `CRL_packs/crl_config.json` 添加资源包配置
5. 执行 `/crladmin reload` 重新加载配置

### 客户端

1. 将模组 JAR 放入客户端 `mods/` 目录
2. 连接到已安装服务端模组的服务器即可自动同步资源包列表

## 命令

### 管理员命令（服务端）

| 命令 | 说明 |
|------|------|
| `/crladmin` | 显示帮助信息 |
| `/crladmin reload` | 重新加载配置 |
| `/crladmin mandatory_check` | 查看必选资源包检查状态 |
| `/crladmin mandatory_check [true/false]` | 启用/禁用必选资源包检查 |
| `/crladmin setport` | 查看当前传输端口 |
| `/crladmin setport [端口号]` | 设置固定传输端口 (1-65535) |

### 客户端命令

| 命令 | 说明 |
|------|------|
| `/crl` | 显示帮助信息 |
| `/crl settings` | 打开 CRL 设置界面 |
| `/crl packs` | 打开加密资源包管理界面 |
| `/crl download_packs` | 打开资源包下载管理界面 |

## 配置文件

配置文件位于服务端 `CRL_packs/crl_config.json`：

```json
{
  "rateLimitMbPerSec": 10.0,
  "mandatoryCheck": false,
  "transferPort": 0,
  "packs": [
    {
      "packId": "my-pack",
      "displayName": "我的资源包",
      "fileName": "my-pack.zip",
      "sha256": "自动计算(无需填写)",
      "fileSize": 0,
      "encrypted": true,
      "required": true,
      "decryptKey": "your-decrypt-key"
    }
  ]
}
```

| 字段 | 说明 |
|------|------|
| `rateLimitMbPerSec` | 文件传输速率限制 (MB/s) |
| `mandatoryCheck` | 是否启用必选资源包检查 |
| `transferPort` | 传输端口，0 为自动分配 |
| `packs` | 资源包列表 |

## 许可证

MIT License
