package com.mj.controller;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mj.entity.AiSysUser;
import com.mj.entity.Task;
import com.mj.entity.TaskShare;
import com.mj.service.TaskShareService;
import com.mj.service.TaskStoreService;
import com.mj.utils.PageQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 分享
 */
@RestController
@RequestMapping("/share")
@RequiredArgsConstructor
public class TaskShareController {

    private final TaskStoreService taskStoreService;

    private final TaskShareService taskShareService;

    /**
     * 分享
     */
    @GetMapping("/{taskId}")
    public ResponseEntity share(@PathVariable("taskId") Long taskId) {
        Task task = taskStoreService.getById(taskId);
        if (ObjUtil.isNull(task)) {
            return ResponseEntity.status(NOT_FOUND).body("非法请求");
        }
        TaskShare one = taskShareService.getOne(Wrappers.lambdaQuery(TaskShare.class).eq(TaskShare::getTaskId, taskId));
        if (ObjUtil.isNotNull(one)) {
            return ResponseEntity.status(NOT_FOUND).body("不能重复分享");
        }

        AiSysUser aiSysUser = null;
        if (ObjUtil.isNull(aiSysUser)) {
            return ResponseEntity.status(NOT_FOUND).body("非法请求");
        }
        TaskShare taskShare = new TaskShare();
        taskShare.setTaskId(task.getId());
        taskShare.setPrompt(task.getPrompt());
        taskShare.setOptions(JSONUtil.toJsonStr(task.getOptions()));
        taskShare.setImageUrl(task.getImageUrl());
        taskShare.setUserId(task.getUserId());
        taskShare.setUserName(aiSysUser.getUserName());
        taskShare.setCreateTime(LocalDateTime.now());
        taskShareService.save(taskShare);
        return ResponseEntity.ok(taskShare);
    }
    /**
     * 获取分享列表
     */
    @GetMapping("/list")
    public ResponseEntity list(@RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
                               @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        LambdaQueryWrapper<TaskShare> wrapper = Wrappers.lambdaQuery(TaskShare.class).orderByDesc(TaskShare::getCreateTime);
        Page<TaskShare> sharePage = taskShareService.page(new PageQuery(currentPage, pageSize).build(), wrapper);
        sharePage.convert(share -> {
            share.setOptionsObj(JSONUtil.parseObj(share.getOptions()));
            return share;
        });
        return ResponseEntity.ok(sharePage);

    }
}
