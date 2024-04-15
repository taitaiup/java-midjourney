package com.mj.entity;

import com.mj.enums.TaskAction;
import lombok.Data;

/**
 * discord事件回调内容对象
 */
@Data
public class ContentParseData {
	/**
	 * 提示词
	 */
	protected String prompt;
	/**
	 * 指令
	 */
	private TaskAction action;
	/**
	 * 位置
	 */
	private Integer index;
	/**
	 * 状态
	 */
	protected String status;

}
