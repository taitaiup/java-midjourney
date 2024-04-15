package com.mj.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mj.entity.Task;
import com.mj.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStoreServiceImpl extends ServiceImpl<TaskMapper, Task> implements TaskStoreService {

}
