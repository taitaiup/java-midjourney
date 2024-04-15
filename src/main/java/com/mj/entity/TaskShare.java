package com.mj.entity;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class TaskShare implements Serializable {

	@TableId
	private Long id;

	private Long taskId;
	/**
	 * 提示词
	 */
	private String prompt;
	/**
	 * 图片url
	 */
	private String imageUrl;
	/**
	 * 用户id
	 */
	private Long userId;

	private String userName;

	private LocalDateTime createTime;
	/**
	 * prompt的参数，比如：
	 * {"--ar":"3:4","--no":"桌子"}
	 */
	private String options;

	@TableField(exist = false)
	private JSONObject optionsObj;

}
