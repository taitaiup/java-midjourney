package com.mj.entity;

import lombok.Data;

import java.util.HashMap;

/**
 * Vary Region提交参数
 */
@Data
public class ExecuteRegionDTO {
    /**
     * 提示词
     */
    private String prompt;
    /**
     * 局部重绘的蒙版
     */
    private String mask;
    /**
     * 任务ID
     */
    private Long taskId;
    /**
     * 任务ID
     */
    private String customId;
    /**
     * 参数
     */
    private HashMap<String, String> options;
}
