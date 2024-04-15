package com.mj.service;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.text.CharSequenceUtil;
import com.mj.config.MjProperties;
import com.mj.entity.*;
import com.mj.enums.BlendDimensions;
import com.mj.enums.TaskStatus;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.tmt.v20180321.TmtClient;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateRequest;
import com.tencentcloudapi.tmt.v20180321.models.TextTranslateResponse;
import eu.maxschuster.dataurl.DataUrl;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class TaskServiceImpl implements TaskService {

	private final TaskStoreService taskStoreService;
	private final DiscordService discordService;

	private final ThreadPoolTaskExecutor taskExecutor;
	@Getter
	private final List<Task> runningTasks;
	@Getter
	private final Map<Long, Future<?>> taskFutureMap = Collections.synchronizedMap(new HashMap<>());
	private final TmtClient tmtClient;

	public TaskServiceImpl(MjProperties properties, TaskStoreService taskStoreService, DiscordService discordService, TmtClient tmtClient) {
		this.runningTasks = new CopyOnWriteArrayList<>();
		this.taskStoreService = taskStoreService;
		this.discordService = discordService;
		this.tmtClient = tmtClient;
		MjProperties.TaskQueueConfig queueConfig = properties.getQueue();
		this.taskExecutor = new ThreadPoolTaskExecutor();
		this.taskExecutor.setCorePoolSize(queueConfig.getCoreSize());
		this.taskExecutor.setMaxPoolSize(queueConfig.getCoreSize());
		this.taskExecutor.setQueueCapacity(queueConfig.getQueueSize());
		this.taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		this.taskExecutor.setThreadNamePrefix("TaskQueue-");
		this.taskExecutor.initialize();
	}

	@Override
	public ExecuteResult submitImagine(Task task, List<DataUrl> dataUrls) {
		return submitTask(task, () -> discordService.imagine(task, dataUrls));
	}
	@Override
	public ExecuteResult submitUpscale(Task task, int index, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.upscale(task, index, childMessageId, messageHash));
	}

	@Override
	public ExecuteResult submitVariation(Task task, int index, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.variation(task, index, childMessageId, messageHash));
	}


	@Override
	public ExecuteResult submitReroll(Task task, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.reroll(task, childMessageId, messageHash));
	}

	@Override
	public ExecuteResult submitDescribe(Task task, String taskFileName, DataUrl dataUrl) {
		return submitTask(task, () -> discordService.describe(task, taskFileName, dataUrl));
	}

	@Override
	public ExecuteResult submitBlend(Task task, List<DataUrl> dataUrls, BlendDimensions dimensions) {
		return submitTask(task, () -> discordService.blend(task, dataUrls, dimensions));
	}

	@Override
	public ExecuteResult submitUpscaleX(Task task, String childMessageId, String messageHash, int num) {
		return submitTask(task, () -> discordService.submitUpscaleX(task, childMessageId, messageHash, num));
	}

	@Override
	public ExecuteResult submitOutpaintX(Task task, String childMessageId, String messageHash, int num) {
		return submitTask(task, () -> discordService.submitOutpaintX(task, childMessageId, messageHash, num));
	}

	@Override
	public ExecuteResult submitVariationLow(Task task, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.submitVariationLow(task, childMessageId, messageHash));
	}

	@Override
	public ExecuteResult submitVariationHigh(Task task, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.submitVariationHigh(task, childMessageId, messageHash));
	}

	@Override
	public ExecuteResult submitDirection(String direction, Task task, String childMessageId, String messageHash) {
		return submitTask(task, () -> discordService.submitDirection(direction, task, childMessageId, messageHash));
	}

	@Override
	public ExecuteResult submitRegion(Task task, String childMessageId, String messageHash, String mask, String customId) {
		return submitTask(task, () -> discordService.region(task, childMessageId, messageHash, mask, customId));
	}


	public synchronized ExecuteResult submitTask(Task task, Callable<Message<Void>> discordSubmit) {
		this.taskStoreService.save(task);
		int currentWaitTaskNumbers;
		try {
			currentWaitTaskNumbers = this.taskExecutor.getThreadPoolExecutor().getQueue().size();
			Future<?> future = this.taskExecutor.submit(() -> executeTask(task, discordSubmit));
			this.taskFutureMap.put(task.getId(), future);
		} catch (RejectedExecutionException e) {
			this.taskStoreService.removeById(task.getId());
			return ExecuteResult.fail(ResponseCode.QUEUE_REJECTED, "队列已满，请稍后尝试");
		} catch (Exception e) {
			log.error("submit task error", e);
			return ExecuteResult.fail(ResponseCode.FAILURE, "提交失败，系统异常");
		}
		if (currentWaitTaskNumbers == 0) {
			return ExecuteResult.of(ResponseCode.SUCCESS, "提交成功", task);
		} else {
			return ExecuteResult.of(ResponseCode.IN_QUEUE, "排队中，前面还有【" + currentWaitTaskNumbers + "】个任务", task);
		}
	}
	private void executeTask(Task task, Callable<Message<Void>> discordSubmit) {
		this.runningTasks.add(task);
		try {
			task.start();
			Message<Void> result = discordSubmit.call();
			if (result.getCode() != ResponseCode.SUCCESS) {
				task.fail(result.getDescription());
				this.saveOrUpdate(task);
				return;
			}
			this.saveOrUpdate(task);
			do {
				task.sleep();
				this.saveOrUpdate(task);
			} while (task.getStatus() == TaskStatus.IN_PROGRESS);
			log.debug("task finished, id: {}, status: {}", task.getId(), task.getStatus());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (Exception e) {
			log.error("task execute error", e);
			task.fail("执行错误，系统异常");
			this.saveOrUpdate(task);
		} finally {
			this.runningTasks.remove(task);
			this.taskFutureMap.remove(task.getId());
		}
	}

	/**
	 * 保存更新task
	 */
	private void saveOrUpdate(Task task) {
		this.taskStoreService.saveOrUpdate(task);
	}

	public Task findRunningTaskByCondition(TaskCondition condition) {
		return getRunningTasks().stream().filter(condition).findFirst().orElse(null);
	}

	public Task getRunningTaskByNonce(String nonce) {
		if (CharSequenceUtil.isBlank(nonce)) {
			return null;
		}
		TaskCondition condition = new TaskCondition().setNonce(nonce);
		Task runningTask = findRunningTaskByCondition(condition);
		return runningTask;
	}


	public String translateToEnglish(String prompt) {
		TextTranslateRequest request = new TextTranslateRequest();
		request.setSourceText(prompt);
		request.setSource("zh");
		request.setTarget("en");
		request.setProjectId(0L);
		TextTranslateResponse translateResponse = null;
		try {
			translateResponse = tmtClient.TextTranslate(request);
		} catch (TencentCloudSDKException e) {
			throw new ValidateException("翻译报错：", e);
		}
		return translateResponse.getTargetText();
	}

	@Override
	public String translateToChinese(String prompt) {
		TextTranslateRequest request = new TextTranslateRequest();
		request.setSourceText(prompt);
		request.setSource("en");
		request.setTarget("zh");
		request.setProjectId(0L);
		TextTranslateResponse translateResponse = null;
		try {
			translateResponse = tmtClient.TextTranslate(request);
		} catch (TencentCloudSDKException e) {
			throw new ValidateException("翻译报错：", e);
		}
		return translateResponse.getTargetText();
	}

	/**
	 * 处理runningtask和线程池中正在执行的任务
	 */
	@Override
	public void removeRunningTaskAndFutureMap(List<Task> list) {
		list.forEach(task -> {
			this.runningTasks.remove(task);
			Future<?> future = this.taskFutureMap.get(task.getId());
			if (future != null) {
				future.cancel(true);
			}
			this.taskFutureMap.remove(task.getId());
		});
	}
}
