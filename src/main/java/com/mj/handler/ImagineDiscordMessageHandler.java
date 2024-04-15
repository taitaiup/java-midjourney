package com.mj.handler;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mj.entity.ContentParseData;
import com.mj.entity.Task;
import com.mj.entity.TaskCondition;
import com.mj.enums.TaskAction;
import com.mj.enums.TaskStatus;
import com.mj.service.TaskService;
import com.mj.service.TaskStoreService;
import com.mj.utils.ConvertUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Imagine 命令消息监听处理器
 * 【**消息接收**】 消息id：1209020746425176125，消息类型：SLASH_COMMAND，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (Waiting to start)
 * 【**消息接收**】 消息引用对象是否为空：null
 *
 * 【**消息更新**】 消息id：1209020746425176125，消息类型：SLASH_COMMAND，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (0%) (fast)
 * 【**消息更新**】 消息Interaction：imagine
 * 【**消息更新**】 消息id：1209020746425176125，消息类型：SLASH_COMMAND，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (15%) (fast)
 * 【**消息更新**】 消息Interaction：imagine
 * 【**消息更新**】 消息id：1209020746425176125，消息类型：SLASH_COMMAND，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (31%) (fast)
 * 【**消息更新**】 消息Interaction：imagine
 * 【**消息更新**】 消息id：1209020746425176125，消息类型：SLASH_COMMAND，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (78%) (fast)
 * 【**消息更新**】 消息Interaction：imagine
 *
 * 【**消息接收**】 消息id：1209020903736737792，消息类型：DEFAULT，内容为：**Pure Chinese female singer on campus, singing in the bar** - <@426580409665716235> (fast)
 * 【**消息接收**】 消息引用对象是否为空：null
 */
@Component
@Slf4j
public class ImagineDiscordMessageHandler extends AbstractDiscordMessageHandler {

    @Resource
    private TaskService taskService;
    @Resource
    private TaskStoreService taskStoreService;

    @Override
    public void onMessageReceived(Message message) {
        ContentParseData parseData = ConvertUtils.parseContent(message.getContentRaw());
        String nonce = message.getNonce();
        Task task = null;
        if (CharSequenceUtil.isNotBlank(nonce)) {
            //消息创建
            task = taskService.getRunningTaskByNonce(nonce);
            if (ObjUtil.isNull(task)) {
                log.info("【消息初始化】拒绝，nonce为：{}, 此时运行的task集合为：{}", nonce, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            if (task.getAction().equals(TaskAction.DESCRIBE)) {
                //忽略describe命令的监听，在DescribeDiscordMessageHandler中已经处理了
                return;
            }

            log.info("【消息初始化】成功，任务id：" + task.getId() + "，任务的messageId：" + message.getId() + "，内容为：" + message.getContentRaw());
            //补全数据
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setMessageId(message.getId());
            //自定义参数，blend指令，task没有prompt
            task.setFinalPromptEn(parseData.getPrompt());
            task.awake();
        } else if (MessageType.DEFAULT.equals(message.getType()) && hasImage(message) && parseData != null) {
            List<Message.Attachment> attachments = message.getAttachments();
            String imageUrl = attachments.get(0).getUrl();
            String messageId = message.getId();
            String messageHash = getMessageHash(imageUrl);
            TaskCondition taskCondition = new TaskCondition().setStatusSet(Set.of(TaskStatus.IN_PROGRESS)).setFinalPromptEn(parseData.getPrompt());
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【消息完成】拒绝，收到的监听message内容为：{}，ChildMessageId为：{}，此时运行的task集合为：{}", message.getContentRaw(), messageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【消息完成】成功，图片生成成功，DEFAULT，任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(message.getContentRaw());
            task.success();
            task.awake();
        }
    }
    /**
     * 补全message_hash值和进度条
     */
    @Override
    public void onMessageUpdate(Message message) {
        String messageId = message.getId();
        ContentParseData parseData = ConvertUtils.parseContent(message.getContentRaw());
        if ("0%".equals(parseData.getStatus())) {
            return;
        }
        //通过messageId在数据库如果找不到，意味着，没有调用过初始化接口或者初始化接口失败，就直接更新图片创作进度了
        LambdaQueryWrapper<Task> wrapper = Wrappers.lambdaQuery(Task.class).eq(Task::getStatus, TaskStatus.IN_PROGRESS).eq(Task::getMessageId, messageId);
        Task taskDB = taskStoreService.getOne(wrapper);
        if (ObjUtil.isNull(taskDB)) {
            log.info("messageId为：" + messageId + ", 还没收到消息的初始化通知，就收到了更新通知！！！");
            return;
        }
        //找到对应的task，补全数据
        TaskCondition taskCondition = new TaskCondition().setStatusSet(Set.of(TaskStatus.IN_PROGRESS)).setMessageId(messageId);
        Task task = taskService.findRunningTaskByCondition(taskCondition);
        if (ObjUtil.isNull(task)) {
            log.info("【消息创作中】拒绝，收到的监听message内容为：{}，messageId为：{}，此时运行的task集合为：{}", parseData.getPrompt(), messageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
            return;
        }
        log.info("【消息创作中】更新进度, taskId：{}, 消息的messageId：{}, 进度：{}", task.getId(), messageId, parseData.getStatus());
        List<Message.Attachment> attachments = message.getAttachments();
        if (ObjUtil.isNotNull(attachments) && attachments.size() > 0) {
            String imageUrl = attachments.get(0).getUrl();
            String messageHash = getMessageHash(imageUrl);
            task.setMessageHash(messageHash);
            task.setImageUrl(imageUrl);//蒙版
        }
        task.setProgress(parseData.getStatus());
        task.awake();
    }

    protected boolean hasImage(Message message) {
        List<Message.Attachment> attachments = message.getAttachments();
        return ObjUtil.isNotNull(attachments) && attachments.size() > 0;
    }


}
