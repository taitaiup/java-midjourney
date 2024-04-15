package com.mj.entity;

public final class ResponseCode {
	/**
	 * 成功.
	 */
	public static final int SUCCESS = 200;
	/**
	 * 数据未找到.
	 */
	public static final int NOT_FOUND = 204;
	/**
	 * 校验错误.
	 */
	public static final int BAD_REQUEST = 400;
	/**
	 * 系统异常.
	 */
	public static final int FAILURE = 500;

	/**
	 * 已存在.
	 */
	public static final int EXISTED = 21;
	/**
	 * 排队中.
	 */
	public static final int IN_QUEUE = 22;
	/**
	 * 队列已满.
	 */
	public static final int QUEUE_REJECTED = 23;
	/**
	 * prompt包含敏感词.
	 */
	public static final int BANNED_PROMPT = 24;


}