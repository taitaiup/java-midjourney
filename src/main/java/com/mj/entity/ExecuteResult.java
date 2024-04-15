package com.mj.entity;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ExecuteResult {

	private int code;

	private String description;

	private Object result;

	private Map<String, Object> properties = new HashMap<>();

	public ExecuteResult setProperty(String name, Object value) {
		this.properties.put(name, value);
		return this;
	}

	public ExecuteResult removeProperty(String name) {
		this.properties.remove(name);
		return this;
	}

	public Object getProperty(String name) {
		return this.properties.get(name);
	}

	@SuppressWarnings("unchecked")
	public <T> T getPropertyGeneric(String name) {
		return (T) getProperty(name);
	}

	public <T> T getProperty(String name, Class<T> clz) {
		return clz.cast(getProperty(name));
	}

	public static ExecuteResult of(int code, String description, Object result) {
		return new ExecuteResult(code, description, result);
	}

	public static ExecuteResult fail(int code, String description) {
		return new ExecuteResult(code, description, null);
	}

	private ExecuteResult(int code, String description, Object result) {
		this.code = code;
		this.description = description;
		this.result = result;
	}
}
