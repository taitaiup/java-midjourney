package com.mj.handler;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mj.entity.Task;
import com.mj.entity.TaskCondition;
import com.mj.service.TaskService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Describe 命令消息处理器
 * 先输出消息接收再输出消息变更，消息接收无法识别
 */
@Component
@Slf4j
public class DescribeDiscordMessageHandler extends AbstractDiscordMessageHandler {

    @Resource
    private TaskService taskService;

    @Override
    public void onMessageReceived(Message message) {
        // 消息接收无法识别
    }

    @Override
    public void onMessageUpdate(Message message) {
        List<MessageEmbed> embeds = message.getEmbeds();
        if (embeds.isEmpty()) {
            return;
        }
        MessageEmbed messageEmbed = embeds.get(0);
        if (Objects.isNull(messageEmbed.getImage())) {
            return;
        }
        String imageUrl = messageEmbed.getImage().getUrl();
        if (StrUtil.isBlank(imageUrl)) {
            return;
        }
        int hashStartIndex = imageUrl.lastIndexOf("/");
        // 截取文件名
        //https://cdn.discordapp.com/ephemeral-attachments/1092492867185950852/1204719279992340480/30095.png?ex=65d5c142&is=65c34c42&hm=813891c17cd45a7941f524714130100e196f19f886ba66da8127fde1e5a6ca30&
        String nonce = CharSequenceUtil.subBefore(imageUrl.substring(hashStartIndex + 1), ".", true);
        TaskCondition taskCondition = new TaskCondition().setNonce(nonce);
        Task task = taskService.findRunningTaskByCondition(taskCondition);
        if (task == null) {
            log.info("【DESCRIBE指令消息拒绝】nonce为：{}，此时运行的task集合为：{}", nonce, JSONUtil.toJsonStr(taskService.getRunningTasks()));
            return;
        }
        log.info("【DESCRIBE指令消息完成】任务id：" + task.getId() + "，任务的messageId：" + message.getId());
        String messageHash = getMessageHash(imageUrl);
        task.setMessageHash(messageHash);
        task.setMessageId(message.getId());
        String s = processPrompt(Objects.requireNonNull(messageEmbed.getDescription()));
        task.setPrompt(s);
        task.setPromptEn(messageEmbed.getDescription());
        task.setImageUrl(replaceCdnUrl(imageUrl));
        task.success();
        task.awake();
    }

    /**
     * 去除序号表情，github的开源框架不符合，使用最原始的方法解决
     * 如：1️⃣ 2️⃣ 3️⃣ 4️⃣
     */
    private String processPrompt(String descriptions) {
        String replace = descriptions.replace("\u0031\ufe0f\u20e3\u0020", "1.")
                .replace("\u0032\ufe0f\u20e3\u0020", "2.")
                .replace("\u0033\ufe0f\u20e3\u0020", "3.")
                .replace("\u0034\ufe0f\u20e3\u0020", "4.");
        String prompt = "";
        for (String desc : Arrays.stream(replace.split("\n\n")).toList()) {
            String before = StrUtil.subBefore(desc, "--", false);
            //String param = " --" + StrUtil.subAfter(desc, "--", false);
            String temp = taskService.translateToChinese(before);
            prompt += temp + "\n\n";
        }
        return prompt;
    }
}
