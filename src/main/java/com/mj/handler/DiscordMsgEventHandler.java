package com.mj.handler;

import com.mj.entity.ContentParseData;
import com.mj.utils.ConvertUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Discord中机器人消息的监听器
 */
@Component
@Slf4j
public class DiscordMsgEventHandler extends ListenerAdapter {


    @Resource
    private ImagineDiscordMessageHandler imagineDiscordMessageHandler;
    @Resource
    private UpscaleAndVariationDiscordMessageHandler uvMessageHandler;
    @Resource
    private DescribeDiscordMessageHandler describeMessageHandler;

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        Message message = event.getMessage();
        //System.out.println("【**消息接收**】 消息id：" + message.getId() + "，消息类型：" + message.getType() + "，内容为：" + message.getContentRaw() + "nonce：" + message.getNonce());
        //ContentParseData parseData = ConvertUtils.parseContent(message.getContentRaw());
        //System.out.println("解析内容对象为："+parseData);
        //System.out.println("【**消息接收**】 消息引用对象是否为空：" + message.getReferencedMessage());

        if (MessageType.SLASH_COMMAND.equals(message.getType()) || MessageType.DEFAULT.equals(message.getType())) {
            //imagine、blend指令(暂不用)
            imagineDiscordMessageHandler.onMessageReceived(message);
        } else if (MessageType.INLINE_REPLY.equals(message.getType()) && message.getReferencedMessage() != null) {
            // uv指令、高级指令、reroll、Vary Region
            uvMessageHandler.onMessageReceived(message);
        }
    }

    @Override
    public void onMessageUpdate(@NotNull MessageUpdateEvent event) {
        Message message = event.getMessage();
        ContentParseData parseData = ConvertUtils.parseContent(message.getContentRaw());
        List<Message.Attachment> attachments = message.getAttachments();
        //System.out.println("【**消息更新**】 消息id：" + message.getId() + "，消息类型：" + message.getType() + "，内容为：" + message.getContentRaw());
        //System.out.println("【**消息更新**】 消息Interaction：" + ((Objects.nonNull(message.getInteraction())) ? message.getInteraction().getName() : "命令为空"));
        if (Objects.nonNull(message.getInteraction()) && "describe".equals(message.getInteraction().getName())) {
            // describe指令
            describeMessageHandler.onMessageUpdate(message);
        } else if (Objects.nonNull(message.getInteraction()) && "imagine".equals(message.getInteraction().getName())) {
            // imagine指令进度反显
            imagineDiscordMessageHandler.onMessageUpdate(message);
        }
        //uv不监听update
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        //System.out.println(event);
    }

    @Override
    public void onGenericInteractionCreate(@NotNull GenericInteractionCreateEvent event) {
        super.onGenericInteractionCreate(event);
    }
}
