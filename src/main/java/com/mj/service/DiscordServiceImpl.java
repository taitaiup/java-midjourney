package com.mj.service;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.mj.config.MjProperties;
import com.mj.entity.Message;
import com.mj.entity.ResponseCode;
import com.mj.entity.Task;
import com.mj.enums.BlendDimensions;
import com.mj.support.DiscordHelper;
import com.mj.utils.MimeTypeUtils;
import com.mj.utils.ParamUtil;
import eu.maxschuster.dataurl.DataUrl;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DiscordServiceImpl implements DiscordService {

    private final ApplicationContext applicationContext;
    private final MjProperties mjProperties;
    private final DiscordHelper discordHelper;
    private final Map<String, String> templates = new HashMap<>(28, 0.5F);
    private final String discordServerUrl;
    private final String discordInteractionUrl;
    private final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36";

    public DiscordServiceImpl(ApplicationContext applicationContext, MjProperties mjProperties, DiscordHelper discordHelper) {
        this.applicationContext = applicationContext;
        this.mjProperties = mjProperties;
        this.discordHelper = discordHelper;
        this.discordServerUrl = this.discordHelper.getServer();
        this.discordInteractionUrl = discordServerUrl + "/api/v9/interactions";
    }
    @PostConstruct
    public void initTemplates() throws IOException {
        Resource[] resources = this.applicationContext.getResources("classpath:api-params/*.json");
        for (var resource : resources) {
            String filename = resource.getFilename();
            String params = IoUtil.readUtf8(resource.getInputStream());
            this.templates.put(filename.substring(0, filename.length() - 5), params);
        }
    }

    @Override
    public Message<Void> imagine(Task task, List<DataUrl> dataUrls) {
        List<String> imageUrls = new ArrayList<>();
        List<String> cdnImageUrls = new ArrayList<>();
        for (DataUrl dataUrl : dataUrls) {
            String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
            Message<String> uploadResult = upload(task, taskFileName, dataUrl);
            if (uploadResult.getCode() != ResponseCode.SUCCESS) {
                return Message.of(uploadResult.getCode(), uploadResult.getDescription());
            }
            String finalFileName = uploadResult.getResult();
            Message<String> sendImageResult = sendImageMessage(task, "upload image: " + finalFileName, finalFileName);
            if (sendImageResult.getCode() != ResponseCode.SUCCESS) {
                return Message.of(sendImageResult.getCode(), sendImageResult.getDescription());
            }
            imageUrls.add(sendImageResult.getResult());
        }
        //再获取image的url
        if (!imageUrls.isEmpty()) {
            imageUrls.forEach(url -> {
                cdnImageUrls.add(discordHelper.replaceCdnUrl(url));
            });
            task.setPrompt("/*"+String.join(" ", cdnImageUrls) + "*/" + task.getPrompt());
            task.setPromptEn(String.join(" ", imageUrls) + " " + task.getPromptEn());
            task.setDescription("/imagine " + task.getPrompt());
        }
        return imagineOnly(task);
    }

    private Message<Void> imagineOnly(Task task) {
        //组装参数
        String commonParam = ParamUtil.replaceCommonParams(templates.get("imagine"), mjProperties, task.getNonce());
        JSONObject requsetParam = new JSONObject(commonParam);
        requsetParam.getJSONObject("data").getJSONArray("options").getJSONObject(0)
                .set("value", task.getPromptEn());
        //请求
        return requestDiscordAndResponseByInteraction(task, requsetParam.toString());
    }
    @Override
    public Message<Void> upscale(Task task, int index, String childMessageId, String messageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("upscale"), mjProperties, task.getNonce())
                .replace("$message_id", childMessageId)
                .replace("$index", String.valueOf(index))
                .replace("$message_hash", messageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> variation(Task task, int index, String childMessageId, String messageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("variation"), mjProperties, task.getNonce())
                .replace("$message_id", childMessageId)
                .replace("$index", String.valueOf(index))
                .replace("$message_hash", messageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> reroll(Task task, String childMessageId, String messageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("reroll"), mjProperties, task.getNonce())
                .replace("$message_id", childMessageId)
                .replace("$message_hash", messageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> describe(Task task, String taskFileName, DataUrl dataUrl) {
        Message<String> uploadResult = upload(task, taskFileName, dataUrl);
        if (uploadResult.getCode() != ResponseCode.SUCCESS) {
            return Message.of(uploadResult.getCode(), uploadResult.getDescription());
        }
        String finalFileName = uploadResult.getResult();
        String paramsStr = ParamUtil.replaceCommonParams(templates.get("describe"), mjProperties, task.getNonce())
                                    .replace("$file_name", taskFileName)
                                    .replace("$final_file_name", finalFileName);
        //请求
        return requestDiscordAndResponseByInteraction(task, paramsStr);
    }



    @Override
    public Message<Void> blend(Task task, List<DataUrl> dataUrlList, BlendDimensions dimensions) {
        //task.start();
        List<String> finalFileNames = new ArrayList<>();
        for (DataUrl dataUrl : dataUrlList) {
            String taskFileName = task.getId() + "." + MimeTypeUtils.guessFileSuffix(dataUrl.getMimeType());
            //String finalFileName = upload(task, taskFileName, dataUrl);
            String finalFileName = null;
            finalFileNames.add(finalFileName);
        }
        String paramsStr = ParamUtil.replaceCommonParams(templates.get("blend"), mjProperties, task.getNonce());
        JSONObject params = new JSONObject(paramsStr);
        JSONArray options = params.getJSONObject("data").getJSONArray("options");
        JSONArray attachments = params.getJSONObject("data").getJSONArray("attachments");
        for (int i = 0; i < finalFileNames.size(); i++) {
            String finalFileName = finalFileNames.get(i);
            String fileName = CharSequenceUtil.subAfter(finalFileName, "/", true);
            JSONObject attachment = new JSONObject().put("id", String.valueOf(i))
                    .put("filename", fileName)
                    .put("uploaded_filename", finalFileName);
            attachments.put(attachment);
            JSONObject option = new JSONObject().put("type", 11)
                    .put("name", "image" + (i + 1))
                    .put("value", i);
            options.put(option);
        }
        options.put(new JSONObject().put("type", 3)
                .put("name", "dimensions")
                .put("value", "--ar " + dimensions.getValue()));
        log.info("任务ID:{}, 执行describe命令, 请求参数: {}", task.getId(), params);
        //请求
        return requestDiscordAndResponseByInteraction(task, params.toString());
    }

    @Override
    public Message<String> upload(Task task, String fileName, DataUrl dataUrl) {
        try {
            JSONObject fileObj = new JSONObject();
            fileObj.set("filename", fileName);
            fileObj.set("file_size", dataUrl.getData().length);
            fileObj.set("id", "0");
            JSONArray jsonArray = new JSONArray();
            jsonArray.add(fileObj);
            JSONObject params = new JSONObject()
                    .set("files", jsonArray);
            //请求
            String discordAttachmentUrl = discordServerUrl + "/api/v9/channels/" + mjProperties.getDiscord().getChannelId() + "/attachments";
            log.info("任务id:{}, 执行upload命令, 请求url:{}, 请求参数:{}", task.getId(), discordAttachmentUrl, params.toString());
            HttpResponse response = postDiscord(discordAttachmentUrl, params.toString());
            if (!response.isOk()) {
                log.info("任务id:{}, upload请求, 上传附件，响应失败，响应码:{}，响应结果:{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
                return Message.badRequest("上传图片到discord，upload方法失败，响应码错误");
            }
            log.info("任务id:{}, upload请求, 上传附件，响应成功，响应码:{}，响应结果:{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));

            JSONArray array = new JSONObject(response.body()).getJSONArray("attachments");
            if (array.size() == 0) {
                return Message.badRequest("上传图片到discord，upload方法失败，attachments为空");
            }
            String uploadUrl = array.getJSONObject(0).getStr("upload_url");
            String uploadFileName = array.getJSONObject(0).getStr("upload_filename");
            Message<Void> putFileResult = putFile(task, uploadUrl, dataUrl);
            if (putFileResult.getCode() != ResponseCode.SUCCESS) {
                return Message.of(putFileResult.getCode(), putFileResult.getDescription());
            }
            return Message.success(uploadFileName);
        } catch (Exception e) {
            log.error("上传图片到discord，upload方法报异常", e);
            return Message.badRequest("上传图片到discord，upload方法报异常！");
        }
    }
    private Message<Void> requestDiscordAndResponseByInteraction(Task task, String requestParamStr) {
        return requestDiscordAndResponse(task, discordInteractionUrl, requestParamStr);
    }
    private Message<Void> requestDiscordAndResponse(Task task, String url, String requestParamStr) {
        HttpResponse response = null;
        try {
            log.info("任务id:{}, 执行【{}】指令请求, url:{}, 请求参数：{}", task.getId(), task.getAction(), url, requestParamStr);
            response = postDiscord(url, requestParamStr);
            if (!response.isOk()) {
                log.info("任务id:{}, 执行【{}】指令请求, 响应失败，响应码:{}，响应结果:{}", task.getId(), task.getAction(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
                return Message.badRequest("请求discord API，响应报错");
            }
            log.info("任务id:{}, 执行【{}】指令请求, 响应成功，响应码:{}，响应结果:{}", task.getId(), task.getAction(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
        } catch (Exception e) {
            log.info("任务id:{}, 执行【{}】指令请求, 出现异常！", task.getId(), task.getAction(), e);
            return Message.failure(e.toString());
        }
        return Message.success();
    }
    private HttpResponse postDiscord(String url, String paramsStr) {
        //请求
        HttpRequest request = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .header("Authorization", mjProperties.getDiscord().getUserToken())
                .header("User-Agent", DEFAULT_USER_AGENT);
        return request.body(paramsStr).execute();
    }
    private Message<Void> putFile(Task task, String uploadUrl, DataUrl dataUrl) {
        //反代
        uploadUrl = this.discordHelper.getDiscordUploadUrl(uploadUrl);
        HttpResponse response = null;
        try {
            HttpRequest request = HttpRequest.put(uploadUrl)
                    .header("Content-Type", dataUrl.getMimeType())
                    .header("Content-Length", Long.toString(dataUrl.getData().length))//限定5M
                    .header("User-Agent", DEFAULT_USER_AGENT);
            response = request.body(dataUrl.getData()).execute();
            if (!response.isOk()) {
                log.error("任务id:{}, putFile接口失败, url:{}, 响应状态:{}, 响应结果:{}", task.getId(), uploadUrl, response.getStatus(), JSONUtil.toJsonStr(response.body()));
                return Message.badRequest("putFile失败");
            }
            log.info("任务id:{}, putFile接口成功, url:{}, 响应状态:{}, 响应结果:{}", task.getId(), uploadUrl, response.getStatus(), JSONUtil.toJsonStr(response.body()));
        } catch (Exception e) {
            log.info("任务id:{}, putFile接口失败:", task.getId(), e);
            return Message.failure(e.toString());
        }
        return Message.success();
    }

    @Override
    public Message<String> sendImageMessage(Task task, String content, String finalFileName) {
        String fileName = CharSequenceUtil.subAfter(finalFileName, "/", true);
        String paramsStr = this.templates.get("message").replace("$content", content)
                .replace("$channel_id", mjProperties.getDiscord().getChannelId())
                .replace("$file_name", fileName)
                .replace("$final_file_name", finalFileName);
        //请求
        String discordMessageUrl = discordServerUrl + "/api/v9/channels/" + mjProperties.getDiscord().getChannelId() + "/messages";
        log.info("任务id:{}, 请求sendImageMessage接口, 请求url: {}, 请求参数: {}", task.getId(), discordMessageUrl, paramsStr);
        HttpResponse response = postDiscord(discordMessageUrl, paramsStr);
        if (!response.isOk()) {
            log.error("任务id:{}, sendImageMessage上传discord失败, 响应码：{}, 响应结果：{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
            return Message.badRequest("发送图片到discord失败");
        }
        log.info("任务id:{}, sendImageMessage上传discord成功, 响应码：{}, 响应结果：{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
        JSONArray attachments = new JSONObject(response.body()).getJSONArray("attachments");
        if (!attachments.isEmpty()) {
            return Message.success(attachments.getJSONObject(0).getStr("url"));
        }
        return Message.failure("发送图片消息到discord失败: 图片不存在");
    }

    @Override
    public Message<Void> submitUpscaleX(Task task, String targetMessageId, String targetMessageHash, int num) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("upsample"+num+"x"), mjProperties, task.getNonce())
                .replace("$message_id", targetMessageId)
                .replace("$message_hash", targetMessageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        log.info("任务id:{}, 执行submitUpscaleX命令, 请求参数: {}", task.getId(), requsetParam);
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> submitOutpaintX(Task task, String targetMessageId, String targetMessageHash, int num) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("outpaint"+num+"x"), mjProperties, task.getNonce())
                .replace("$message_id", targetMessageId)
                .replace("$message_hash", targetMessageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        log.info("任务id:{}, 执行submitOutpaintX命令, 请求参数: {}", task.getId(), requsetParam);
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> submitVariationLow(Task task, String targetMessageId, String targetMessageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("variationlow"), mjProperties, task.getNonce())
                .replace("$message_id", targetMessageId)
                .replace("$message_hash", targetMessageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        log.info("任务id:{}, 执行submitVariationLow命令, 请求参数: {}", task.getId(), requsetParam);
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> submitVariationHigh(Task task, String targetMessageId, String targetMessageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("variationhigh"), mjProperties, task.getNonce())
                .replace("$message_id", targetMessageId)
                .replace("$message_hash", targetMessageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        log.info("任务id:{}, 执行submitVariationHigh命令, 请求参数: {}", task.getId(), requsetParam);
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }

    @Override
    public Message<Void> submitDirection(String direction, Task task, String targetMessageId, String targetMessageHash) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("direction"), mjProperties, task.getNonce())
                .replace("$message_id", targetMessageId)
                .replace("$direction", direction)
                .replace("$message_hash", targetMessageHash);
        String requsetParam = new JSONObject(commonParam).put("message_flags", 0).toString();
        log.info("任务id:{}, 执行submitDirection命令, 请求参数: {}", task.getId(), requsetParam);
        return requestDiscordAndResponseByInteraction(task, requsetParam);
    }
    @Override
    public Message<Void> region(Task task, String childMessageId, String messageHash, String mask, String customId) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("vary_region"), mjProperties, task.getNonce())
                .replace("$message_id", childMessageId)
                .replace("$message_hash", messageHash);
        log.info("任务id:{}, 执行【REGION】指令请求, 请求参数: {}", task.getId(), commonParam);
        Message<Void> voidMessage = requestDiscordAndResponseByInteraction(task, commonParam);
        Message<Void> message = null;
        if (voidMessage.getCode() == ResponseCode.SUCCESS) {
            log.info("任务id:{}, vary region 点击响应成功", task.getId());
            //message = submitJobRegion(task, mask, customId);
            //System.out.println("任务id:{}, Vary Region指令的submit-job接口响应为："  + message.getCode() + "==" + message.getResult() + "==" + message.getDescription());
        }
        return voidMessage;
    }

    /**
     * Vary Region内部的提交任务
     */
    public Message<Void> submitJobRegion(Task task, String mask, String customId) {
        String commonParam = ParamUtil.replaceCommonParams(templates.get("submit_job"), mjProperties, task.getNonce())
                .replace("$customId", customId)
                .replace("$mask", mask)
                .replace("$prompt", task.getPromptEn());
        String requsetParam = new JSONObject(commonParam).toString();
        try {
            //请求 936929561302675456 为 Midjourney Bot的id
            String submitJobUrl = "https://936929561302675456.discordsays.com/inpaint/api/submit-job";
            log.info("任务id:{}, 执行Vary Region submit-job命令, 请求参数: {}", task.getId(), requsetParam);
            HttpResponse response = postDiscord(submitJobUrl, requsetParam);
            if (!response.isOk()) {
                log.info("任务id:{}, Vary Region的submit-job请求失败，响应码：{}，响应结果：{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
                return Message.badRequest("Vary Region的submit-job请求失败");
            }
            log.info("任务id:{}, Vary Region的submit-job请求成功，响应码：{}，响应结果：{}", task.getId(), response.getStatus(), JSONUtil.toJsonStr(response.body()));
        } catch (Exception e) {
            log.info("任务id:{}, Vary Region的submit-job请求出现异常！", task.getId(), e);
            return Message.failure(e.toString());
        }
        return Message.success();
    }

}
