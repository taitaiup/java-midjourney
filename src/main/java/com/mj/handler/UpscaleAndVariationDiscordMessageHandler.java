package com.mj.handler;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mj.entity.ContentParseData;
import com.mj.entity.Task;
import com.mj.entity.TaskCondition;
import com.mj.enums.TaskAction;
import com.mj.service.TaskService;
import com.mj.utils.ConvertUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import net.dv8tion.jda.api.entities.Message;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mj.enums.TaskAction.*;


/**
 * U
 * 格式为：**Ancient buildings with blue sky and white clouds** - Image #4 <@426580409665716235>
 */
@Component
@Slf4j
public class UpscaleAndVariationDiscordMessageHandler extends AbstractDiscordMessageHandler {

    /**
     * uv 消息正则
     * 格式：**The street stalls selling watermelons are excited to watch the game and score goals.** - Variations (Strong) by <@426580409665716235> (fast)
     */
    private static final Pattern MJ_UV_CONTENT_REGEX_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\* - (.*?) by <@(\\d+)> \\((.*?)\\)");

    /**
     * u 消息正则
     * 格式：**Pure Chinese female singer on campus, singing in the bar** - Image #3 <@426580409665716235>
     */
    private static final Pattern MJ_U_CONTENT_REGEX_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\* - Image #(\\d) <@(\\d+)>");
    private static final String CONTENT_REGEX_REROLL = "\\*\\*(.*?)\\*\\* - <@\\d+> \\((.*?)\\)";

    @Resource
    private TaskService taskService;

    @Override
    public void onMessageReceived(Message message) {
        String messageId = message.getId();
        String contentRaw = message.getContentRaw();
        List<Message.Attachment> attachments = message.getAttachments();
        String imageUrl = attachments.get(0).getUrl();
        String messageHash = getMessageHash(imageUrl);
        String parentMessageId = message.getReferencedMessage().getId();
        //先判断是不是reroll指令的监听，再判断其他监听
        ContentParseData rerollParseData = ConvertUtils.parseContent(contentRaw, CONTENT_REGEX_REROLL);
        if (null != rerollParseData) {
            TaskCondition condition = new TaskCondition()
                    .setActionSet(Set.of(TaskAction.REROLL))
                    .setContainPrompt(rerollParseData.getPrompt());
            Task task = taskService.findRunningTaskByCondition(condition);
            if (task == null) {
                log.info("【REROLL指令消息拒绝】内容为：{}，此时运行的task集合为：{}", contentRaw, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【REROLL指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setMessageHash(messageHash);
            task.setContentMj(contentRaw);
            if (StrUtil.isNotBlank(contentRaw) && contentRaw.contains("--v")) {
                //如果包含--v 则是有版本，需要记录，先用这种硬方法来截取版本号，后续想到好方法再换
                String version = contentRaw.substring(contentRaw.lastIndexOf("--v") + 4, contentRaw.lastIndexOf("--v") + 7);
                task.setVersion(version);
            }
            task.success();
            task.awake();
        }
        ContentParseData parseData = ConvertUtils.parseContentAction(contentRaw);
        if (Objects.isNull(parseData)) {
            log.error("引用的messageId：{}，监听到的内容解析不正确，内容为：{}", parentMessageId, contentRaw);
            return;
        }
        Task task = null;
        if (parseData.getAction() == VARIATION) {
            //指令v 或者 指令Variations (Strong)，监听反馈都是一样的，无法区分，先写成这个，后面在runningtask中判断
            TaskCondition taskCondition = new TaskCondition().setPromptEn(parseData.getPrompt())
                    .setActionSet(Set.of(VARIATION))
                    .setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                TaskCondition taskConditionNew = new TaskCondition().setPromptEn(parseData.getPrompt()).setActionSet(Set.of(VARY_STRONG)).setMessageId(parentMessageId);
                task = taskService.findRunningTaskByCondition(taskConditionNew);
                if (ObjUtil.isNull(task)) {
                    log.info("【VARIATION指令或者VARY_STRONG指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                    return;
                }
            }
            log.info("【VARIATION指令或者VARY_STRONG指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == UPSCALE) {
            //指令u Upscale
            TaskCondition taskCondition = new TaskCondition().setPromptEn(parseData.getPrompt())
                    // 连续点击u1、u2监听到的消息，映射不到指定的task，增加索引过滤条件，用description过滤
                    // /change 1210535784709365811 U1
                    .setDescription("/change " + parentMessageId + " U" + parseData.getIndex())
                    .setActionSet(Set.of(UPSCALE))
                    .setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【UPSCALE指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【UPSCALE指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == UPSCALE_SUBTLE) {
            //Upscaled (Subtle)
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(UPSCALE_SUBTLE)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【UPSCALE_SUBTLE指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【UPSCALE_SUBTLE指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == UPSCALE_CREATIVE) {
            //Upscaled (Creative)
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(UPSCALE_CREATIVE)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【UPSCALE_CREATIVE指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【UPSCALE_CREATIVE指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == OUTPAINT15X) {
            //Zoom Out 2x和Zoom Out 1.5x 在事件监听上没发现区别，无法区分，统一走1.5x吧
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(OUTPAINT15X)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                TaskCondition taskConditionNew = new TaskCondition().setPromptEn(parseData.getPrompt()).setActionSet(Set.of(OUTPAINT2X)).setMessageId(parentMessageId);
                task = taskService.findRunningTaskByCondition(taskConditionNew);
                if (ObjUtil.isNull(task)) {
                    log.info("【ZOOM OUT指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                    return;
                }
            }
            log.info("【ZOOM OUT指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == VARY_SUBTLE) {
            //Variations (Subtle)
            TaskCondition taskCondition = new TaskCondition().setPromptEn(parseData.getPrompt()).setActionSet(Set.of(VARY_SUBTLE)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【VARY_SUBTLE指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【VARY_SUBTLE指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == DIRECTION_UP) {
            //往上延伸生成四张图，因为监听到的事件，discord给加了--ar 2:3参数，所以没有添加prompt关键词
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(DIRECTION_UP)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【DIRECTION_UP指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【DIRECTION_UP指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == DIRECTION_DOWN) {
            //往下延伸生成四张图
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(DIRECTION_DOWN)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【DIRECTION_DOWN指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【DIRECTION_DOWN指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == DIRECTION_LEFT) {
            //往左延伸生成四张图
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(DIRECTION_LEFT)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【DIRECTION_LEFT指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【DIRECTION_LEFT指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == DIRECTION_RIGHT) {
            //往右延伸生成四张图
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(DIRECTION_RIGHT)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【DIRECTION_RIGHT指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【DIRECTION_RIGHT指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + task.getMessageId() + "，内容为：" + message.getContentRaw());
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
        if (parseData.getAction() == REGION) {
            //往右延伸生成四张图
            TaskCondition taskCondition = new TaskCondition().setActionSet(Set.of(REGION)).setMessageId(parentMessageId);
            task = taskService.findRunningTaskByCondition(taskCondition);
            if (ObjUtil.isNull(task)) {
                log.info("【VARY_REGION指令消息拒绝】任务的messageId：{}，此时运行的task集合为：{}", parentMessageId, JSONUtil.toJsonStr(taskService.getRunningTasks()));
                return;
            }
            log.info("【VARY_REGION指令消息完成】任务id：" + task.getId() + "，任务的childMessageId：" + messageId + "，任务的messageId：" + parentMessageId + "，内容为：" + contentRaw);
            task.setChildMessageId(messageId);
            task.setMessageHash(messageHash);
            task.setImageUrl(replaceCdnUrl(imageUrl));
            task.setContentMj(contentRaw);
            task.success();
            task.awake();
        }
    }

    @Override
    public void onMessageUpdate(Message message) {
        //该方法无法进行监听消息的区分，不做操作
    }
    /**
     * 匹配UV
     */
    public static ContentParseData matchUpscaleAndVariationMessage(String content) {
        /**
         * "\\*\\*(.*?)\\*\\* - (.*?) by <@(\\d+)> \\((.*?)\\)"
         * Upscaled (Creative) : **Pure Chinese female singer on campus, singing in the bar** - Upscaled (Creative) by <@426580409665716235> (fast)
         * Upscaled (Subtle) : **Pure Chinese female singer on campus, singing in the bar** - Upscaled (Subtle) by <@426580409665716235> (fast)
         * Zoom Out : **Pure Chinese female singer on campus, singing in the bar** - Zoom Out by <@426580409665716235> (fast)
         * uv-Upscale : **Pure Chinese female singer on campus, singing in the bar** - Image #1 <@426580409665716235>
         * uv-Variation : **Pure Chinese female singer on campus, singing in the bar** - Variations (Strong) by <@426580409665716235> (fast)
         * Vary (Subtle) : **Pure Chinese female singer on campus, singing in the bar** - Variations (Subtle) by <@426580409665716235> (fast)
         * Vary (Strong) : **Pure Chinese female singer on campus, singing in the bar** - Variations (Strong) by <@426580409665716235> (fast)
         * 向左：**Chinese rock singers swing together --ar 3:2** - Pan Left by <@426580409665716235> (fast)
         * 向上：**Chinese rock singers swing together --ar 2:3** - Pan Up by <@426580409665716235> (fast)
         * Vary Region：**Lou Teeth Smile --v 6.0** - Variations (Region) by <@426580409665716235> (relaxed)
         */
        Matcher matcher = MJ_UV_CONTENT_REGEX_PATTERN.matcher(content);
        if (!matcher.find()) {
            return matchUpscaleContent(content);
        }
        ContentParseData parseData = new ContentParseData();
        parseData.setPrompt(matcher.group(1));
        String matchAction = matcher.group(2);
        if (matchAction.contains("Variations (Strong)")) {
            //指令v 或者 指令Vary(strong)，监听反馈都是一样的，无法区分，先写成这个，后面在runningtask中判断
            parseData.setAction(VARIATION);
        } else if (matchAction.contains("Upscaled (Creative)")) {
            //Upscaled (Creative)
            parseData.setAction(TaskAction.UPSCALE_CREATIVE);
        } else if (matchAction.contains("Upscaled (Subtle)")) {
            //Upscaled (Subtle)
            parseData.setAction(UPSCALE_SUBTLE);
        } else if (matchAction.contains("Zoom Out")) {
            //Zoom Out, 先默认是zoom out 1.5x，后续查找为空再改成 zoom out 2x
            parseData.setAction(OUTPAINT15X);
        } else if (matchAction.contains("Variations (Subtle)")) {
            //Vary (Subtle)
            parseData.setAction(VARY_SUBTLE);
        } else if (matchAction.contains("Pan Left")) {
            //向左
            parseData.setAction(DIRECTION_LEFT);
        } else if (matchAction.contains("Pan Right")) {
            //向右
            parseData.setAction(DIRECTION_RIGHT);
        } else if (matchAction.contains("Pan Up")) {
            //向上
            parseData.setAction(DIRECTION_UP);
        } else if (matchAction.contains("Pan Down")) {
            //向下
            parseData.setAction(DIRECTION_DOWN);
        } else if (matchAction.contains("Variations (Region)")) {
            parseData.setAction(REGION);
        } else {
            //u
            parseData.setAction(UPSCALE);
        }
        parseData.setStatus(matcher.group(4));
        return parseData;
    }
    private static ContentParseData matchUpscaleContent(String content) {
        Matcher matcher = MJ_U_CONTENT_REGEX_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        ContentParseData parseData = new ContentParseData();
        parseData.setPrompt(matcher.group(1));
        parseData.setAction(UPSCALE);
        parseData.setIndex(Integer.valueOf(matcher.group(2)));
        return parseData;
    }
}
