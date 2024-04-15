package com.mj.entity;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.mj.enums.TaskAction;
import com.mj.enums.TaskStatus;
import lombok.Data;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 任务对象，一条命令对应一个任务
 */
@Data
public class Task extends BaseTask {

	@TableId
	protected Long id;
	/**
	 * 该任务是基于哪个父任务
	 */
	protected Long baseTaskId;

	@Serial
	private static final long serialVersionUID = -674915748204390789L;


	/**
	 * 任务类型
	 */
	private TaskAction action;
	/**
	 * 任务状态
	 */
	private TaskStatus status = TaskStatus.INITIALIZE;
	/**
	 * 提示词
	 */
	private String prompt;
	/**
	 * 提示词-英文
	 */
	private String promptEn;
	/**
	 * vary_region蒙版
	 */
	@TableField(exist = false)
	private String mask;
	/**
	 * 任务描述，目前只有V指令的监听用到了
	 * 格式如下：
	 * 放大/缩小：/uz taskId actionName
	 * 轻微/强烈变化：/vary taskId actionName
	 * 上下左右方向：/direct taskId actionName
	 * REROLL：/change 当前消息的messageId REROLL 父taskId
	 * UV：/change 当前消息的messageId U/V索引
	 * describe：/describe taskFileName
	 * imagine：/imagine " + prompt
	 */
	private String description;
	/**
	 * prompt的参数，比如：
	 * {"--ar":"3:4","--no":"桌子"}
	 */
	private String options;
	/**
	 * options的对象格式，返前端用
	 */
	@TableField(exist = false)
	private JSONObject optionsObj;

	/**
	 * 提交时间
	 */
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)		// 反序列化
	@JsonSerialize(using = LocalDateTimeSerializer.class)
	private LocalDateTime submitTime;
	/**
	 * 开始执行时间
	 */
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)		// 反序列化
	@JsonSerialize(using = LocalDateTimeSerializer.class)
	private LocalDateTime startTime;
	/**
	 * 执行结束时间
	 */
	@JsonDeserialize(using = LocalDateTimeDeserializer.class)		// 反序列化
	@JsonSerialize(using = LocalDateTimeSerializer.class)
	private LocalDateTime finishTime;
	/**
	 * 图片url
	 */
	private String imageUrl;
	/**
	 * 任务进度
	 */
	private String progress;
	/**
	 * 任务失败的原因
	 */
	private String failReason;
	/**
	 * 监听到的prompt内容，原文
	 */
	private String contentMj;
	/**
	 * 唯一表示一次task，作用和task.id差不多
	 * 值为：System.currentTimeMillis() + RandomUtil.randomNumbers(3)
	 */
	private String nonce;
	/**
	 * 当前消息的messageId，也就是父消息的childMessageId
	 */
	private String messageId;
	/**
	 * 回复当前消息的消息ID，messageId
	 */
	private String childMessageId;
	/**
	 * discord的message_hash，用于请求时的参数
	 */
	private String messageHash;
	/**
	 * 存放ws监听来的prompt，因为像blend指令，没有传prompt，监听的时候，就没办法拿promptEn来做过滤
	 */
	@TableField(exist = false)
	private String finalPromptEn;
	/**
	 * 用户id
	 */
	private Long userId;
	@TableField(exist = false)
	private String userName;
	/**
	 * 当前创作执行的版本，只有reroll的命令，服务器是返回版本的
	 */
	private String version;
	/**
	 * 执行VaryRegion时用到
	 */
	@TableField(exist = false)
	private String customId;


	public void initialize() {
		this.submitTime = LocalDateTime.now();
		this.status = TaskStatus.INITIALIZE;
		this.progress = "0%";
		this.nonce = System.currentTimeMillis() + RandomUtil.randomNumbers(3) + "";
	}
	public void start() {
		this.startTime = LocalDateTime.now();
		this.status = TaskStatus.SUBMITTED;
		this.progress = "0%";
	}

	public void success() {
		this.finishTime = LocalDateTime.now();
		this.status = TaskStatus.SUCCESS;
		this.progress = "100%";
	}

	public void fail(String reason) {
		this.finishTime = LocalDateTime.now();
		this.status = TaskStatus.FAILURE;
		this.failReason = reason;
		this.progress = "";
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Task task = (Task) o;
		return Objects.equals(id, task.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}
}
