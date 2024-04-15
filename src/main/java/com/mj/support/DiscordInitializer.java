package com.mj.support;


import com.mj.config.MjProperties;
import com.mj.handler.DiscordMsgEventHandler;
import com.mj.handler.RegionWebSocketListener;
import com.mj.service.DiscordService;
import com.mj.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestConfig;
import net.dv8tion.jda.api.utils.SessionControllerAdapter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * DiscordAccount初始化器
 */
@Slf4j
@Component
public class DiscordInitializer implements ApplicationRunner {

	private final MjProperties mjProperties;

	private final DiscordMsgEventHandler discordMsgEventHandler;

	private final DiscordHelper discordHelper;

	private final TaskService taskService;
	private final DiscordService discordService;


	public DiscordInitializer(MjProperties mjProperties, DiscordMsgEventHandler discordMsgEventHandler, DiscordHelper discordHelper, TaskService taskService, DiscordService discordService) {
		this.mjProperties = mjProperties;
		this.discordMsgEventHandler = discordMsgEventHandler;
		this.discordHelper = discordHelper;
		this.taskService = taskService;
		this.discordService = discordService;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		initJDA();
		initWSS();
	}

	private void initJDA() throws InterruptedException {
		EnumSet<GatewayIntent> intents = EnumSet.of(
				GatewayIntent.GUILD_MESSAGES,
				GatewayIntent.GUILD_MESSAGE_REACTIONS,
				GatewayIntent.MESSAGE_CONTENT
		);
		JDABuilder jdaBuilder = JDABuilder.createDefault(mjProperties.getDiscord().getBotToken(), intents);
		jdaBuilder.setSessionController(new SessionControllerAdapter() {
			@Override
			public @NotNull String getGateway() {
				return discordHelper.getWss();
			}
		});
		jdaBuilder.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS);
		RestConfig restConfig = new RestConfig().setBaseUrl(discordHelper.getServer() + "/api/v" + JDAInfo.DISCORD_REST_VERSION + "/");
		jdaBuilder.setRestConfig(restConfig);
		jdaBuilder.addEventListeners(discordMsgEventHandler);
		JDA build = jdaBuilder.build();
		build.awaitReady();
	}
	private void initWSS() throws Exception {
		RegionWebSocketListener webSocketListener = new RegionWebSocketListener(mjProperties, taskService, discordService, discordHelper);
		webSocketListener.startWss();
	}

}
