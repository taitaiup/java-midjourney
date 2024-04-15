package com.mj.entity;

import com.mj.enums.TaskAction;
import lombok.Data;

@Data
public class BaseExecuteDTO {

    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 任务类型
     */
    private TaskAction action;
    /**
     * 序号(1~4), action为UPSCALE,VARIATION时必传
     */
    private Integer index;


}
