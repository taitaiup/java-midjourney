package com.mj.controller;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mj.entity.FeiShuSysUser;
import com.mj.entity.Task;
import com.mj.service.TaskStoreService;
import com.mj.service.UserCenterService;
import com.mj.utils.PageQuery;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;

@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskStoreService taskStoreService;

    @Resource
    private UserCenterService userCenterService;

    /**
     * 获取某个用户的操作记录，分页
     */
    @GetMapping("/list")
    public ResponseEntity<Object> getTaskListByUserId(@RequestParam(value = "currentPage", required = false, defaultValue = "1") Integer currentPage,
                                                      @RequestParam(value = "pageSize", required = false, defaultValue = "10") Integer pageSize) {
        FeiShuSysUser fromUser = userCenterService.transferUCenterId(1L);
        LambdaQueryWrapper<Task> wrapper = Wrappers.lambdaQuery(Task.class).eq(Task::getUserId, fromUser.getId()).orderByDesc(Task::getSubmitTime);
        Page<Task> page = taskStoreService.page(new PageQuery(currentPage, pageSize).build(), wrapper);
        page.getRecords().forEach(task -> task.setOptionsObj(JSONUtil.parseObj(task.getOptions())));
        return ResponseEntity.ok(page);
    }

    /**
     * 查询具体任务
     */
    @GetMapping("/status/{id}")
    public ResponseEntity<Object> getTaskStatus(@PathVariable("id") long id) {
        Task task = taskStoreService.getById(id);
        if (ObjUtil.isNull(task)) {
            return ResponseEntity.status(NOT_FOUND).body("非法请求");
        }
        JSONObject jsonObject = JSONUtil.parseObj(task.getOptions());
        task.setOptionsObj(jsonObject);
        return ResponseEntity.status(OK).body(task);
    }
}
