package com.mj.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.List;

/**
 * Imagine提交参数
 */
@Data
public class ExecuteImagineDTO {
    /**
     * 提示词
     */
    private String prompt;
    /**
     * 垫图base64数组
     */
    private List<String> base64Array;
    /**
     * 垫图base64
     */
    private String base64;

    /**
     * 参数
     */
    private HashMap<String, String> options;

}
