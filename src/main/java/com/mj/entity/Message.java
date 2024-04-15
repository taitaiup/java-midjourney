package com.mj.entity;

import lombok.Getter;

@Getter
public class Message<T> {
	private final int code;
	private final String description;
	private final T result;

	public static <Y> Message<Y> success() {
		return new Message<>(ResponseCode.SUCCESS, "成功");
	}

	public static <T> Message<T> success(T result) {
		return new Message<>(ResponseCode.SUCCESS, "成功", result);
	}

	public static <T> Message<T> success(int code, String description, T result) {
		return new Message<>(code, description, result);
	}

	public static <Y> Message<Y> notFound() {
		return new Message<>(ResponseCode.NOT_FOUND, "数据未找到");
	}

	public static <Y> Message<Y> badRequest(String description) {
		return new Message<>(ResponseCode.BAD_REQUEST, description);
	}

	public static <Y> Message<Y> failure() {
		return new Message<>(ResponseCode.FAILURE, "系统异常");
	}

	public static <Y> Message<Y> failure(String description) {
		return new Message<>(ResponseCode.FAILURE, description);
	}

	public static <Y> Message<Y> of(int code, String description) {
		return new Message<>(code, description);
	}

	public static <T> Message<T> of(int code, String description, T result) {
		return new Message<>(code, description, result);
	}

	private Message(int code, String description) {
		this(code, description, null);
	}

	private Message(int code, String description, T result) {
		this.code = code;
		this.description = description;
		this.result = result;
	}
}
