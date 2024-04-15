package com.mj.support;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mj.config.MjProperties;
import com.mj.entity.Task;
import com.mj.enums.TaskStatus;
import com.mj.service.TaskService;
import com.mj.service.TaskStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTimeoutSchedule {
	private final TaskService taskService;
	private final TaskStoreService taskStoreService;
	private final MjProperties mjProperties;

	//@Scheduled(fixedRate = 30000L)
	public void checkRunningTasks() {
		long timeout = mjProperties.getQueue().getTimeoutMinutes() * 60 * 1000;
		//超时的直接取消
		List<Task> list = taskService.getRunningTasks().stream().filter(t -> {
			long startTime = t.getStartTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
			return (System.currentTimeMillis() - startTime) > timeout;
		}).toList();
		for (Task task : list) {
			taskService.getRunningTasks().remove(task);
		}
	}
	@Scheduled(fixedRate = 60000L)
	public void checkTasks() {
		LocalDateTime minusMinutes = LocalDateTime.now().minusMinutes(3);
		//1分钟之前的
		LambdaQueryWrapper<Task> wrapper = Wrappers.lambdaQuery(Task.class)
				.lt(Task::getSubmitTime, minusMinutes)
				.in(Task::getStatus, Set.of(TaskStatus.SUBMITTED, TaskStatus.IN_PROGRESS, TaskStatus.INITIALIZE));
		List<Task> timeoutTasks = taskStoreService.list(wrapper);
		if (CollUtil.isNotEmpty(timeoutTasks)) {
			List<Task> list = timeoutTasks.stream().map(task -> {
				task.setStatus(TaskStatus.FAILURE);
				log.info("任务id:{}, 任务执行超时，强行取消！", task.getId());
				return task;
			}).toList();
			taskStoreService.saveOrUpdateBatch(list);
			taskService.removeRunningTaskAndFutureMap(list);
		}
	}
}
