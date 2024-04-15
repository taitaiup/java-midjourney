package com.mj.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "mj")
public class MjProperties {

    /**
     * discord配置.
     */
    private final DiscordConfig discord = new DiscordConfig();
    /**
     * 任务队列配置.
     */
    private final TaskQueueConfig queue = new TaskQueueConfig();

    /**
     * 反代配置.
     */
    private final NgDiscordConfig ngDiscord = new NgDiscordConfig();
    /**
     * 代理配置.
     */
    private final ProxyConfig proxy = new ProxyConfig();

    @Data
    public static class ProxyConfig {
        /**
         * 代理host.
         */
        private String host;
        /**
         * 代理端口.
         */
        private Integer port;
    }
    @Data
    public static class DiscordConfig {
        /**
         * 用户token
         */
        private String userToken;
        /**
         * 机器人token
         */
        private String botToken;
        /**
         * 服务器ID.
         */
        private String guildId;
        /**
         * 频道ID.
         */
        private String channelId;

        private String userAgent;
    }
    @Data
    public static class NgDiscordConfig {
        /**
         * https://discord.com 反代.
         */
        private String server;
        /**
         * https://cdn.discordapp.com 反代.
         */
        private String cdn;
        /**
         * wss://gateway.discord.gg 反代.
         */
        private String wss;
        /**
         * https://discord-attachments-uploads-prd.storage.googleapis.com 反代.
         */
        private String uploadServer;
    }
    /**
     * 任务队列配置
     */
    @Data
    public static class TaskQueueConfig {
        /**
         * 并发数.
         */
        private int coreSize = 16;
        /**
         * 等待队列长度.
         */
        private int queueSize = 10;
        /**
         * 任务超时时间(分钟).
         */
        private int timeoutMinutes = 5;
    }


}