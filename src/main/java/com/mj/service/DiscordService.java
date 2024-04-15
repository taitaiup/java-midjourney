package com.mj.service;

import com.mj.entity.Message;
import com.mj.entity.Task;
import com.mj.enums.BlendDimensions;
import eu.maxschuster.dataurl.DataUrl;

import java.util.List;

/**
 * 与discord交互的接口
 */
public interface DiscordService {
    /**
     * /imagine指令
     *
     * @return
     */
    Message<Void> imagine(Task task, List<DataUrl> dataUrls);

    Message<Void> upscale(Task task, int index, String childMessageId, String messageHash);

    Message<Void> variation(Task task, int index, String childMessageId, String messageHash);

    Message<Void> reroll(Task task, String childMessageId, String messageHash);

    Message<Void> describe(Task task, String taskFileName, DataUrl dataUrl);

    Message<Void> blend(Task task, List<DataUrl> dataUrlList, BlendDimensions dimensions);

    Message<String> upload(Task task, String fileName, DataUrl dataUrl);

    Message<String> sendImageMessage(Task task, String content, String finalFileName);

    Message<Void> submitUpscaleX(Task task, String targetMessageId, String targetMessageHash, int num);
    Message<Void> submitOutpaintX(Task task, String targetMessageId, String targetMessageHash, int num);

    Message<Void> submitVariationLow(Task task, String targetMessageId, String targetMessageHash);

    Message<Void> submitVariationHigh(Task task, String targetMessageId, String targetMessageHash);
    Message<Void> submitDirection(String direction,Task task, String targetMessageId, String targetMessageHash);

    Message<Void> region(Task task, String childMessageId, String messageHash, String mask, String customId);

    Message<Void> submitJobRegion(Task task, String mask, String customId);
}
