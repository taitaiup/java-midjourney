package com.mj.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mj.entity.TaskShare;
import com.mj.mapper.TaskShareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaskShareServiceImpl extends ServiceImpl<TaskShareMapper, TaskShare> implements TaskShareService {

}
