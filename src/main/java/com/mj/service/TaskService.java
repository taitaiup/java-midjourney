package com.mj.service;

import com.mj.entity.ExecuteResult;
import com.mj.entity.Task;
import com.mj.entity.TaskCondition;
import com.mj.enums.BlendDimensions;
import eu.maxschuster.dataurl.DataUrl;

import java.util.List;

public interface TaskService {

	ExecuteResult submitImagine(Task task, List<DataUrl> dataUrls);
	ExecuteResult submitUpscale(Task task, int index, String childMessageId, String messageHash);

	ExecuteResult submitVariation(Task task, int index, String childMessageId, String messageHash);

	ExecuteResult submitReroll(Task task, String childMessageId, String messageHash);

	ExecuteResult submitDescribe(Task task, String taskFileName, DataUrl dataUrl);

	ExecuteResult submitBlend(Task task, List<DataUrl> dataUrls, BlendDimensions dimensions);

	ExecuteResult submitUpscaleX(Task task, String childMessageId, String messageHash, int num);
	
	ExecuteResult submitOutpaintX(Task task, String childMessageId, String messageHash, int num);

	ExecuteResult submitVariationLow(Task task, String childMessageId, String messageHash);

	ExecuteResult submitVariationHigh(Task task, String childMessageId, String messageHash);

	ExecuteResult submitDirection(String direction, Task task, String childMessageId, String messageHash);

	ExecuteResult submitRegion(Task task, String childMessageId, String messageHash, String mask, String customId);

	Task getRunningTaskByNonce(String nonce);
	Task findRunningTaskByCondition(TaskCondition condition);

	List<Task> getRunningTasks();

	String translateToEnglish(String prompt);
	String translateToChinese(String prompt);

	void removeRunningTaskAndFutureMap(List<Task> list);

}