package com.mj.entity;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.ObjUtil;
import com.mj.enums.TaskAction;
import com.mj.enums.TaskStatus;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Set;
import java.util.function.Predicate;


@Data
@Accessors(chain = true)
public class TaskCondition implements Predicate<Task> {
	private Long taskId;

	private Set<TaskStatus> statusSet;
	private Set<TaskAction> actionSet;

	private String prompt;
	//针对 传入的prompt后，监听到的额外增加了默认参数的情况，比如：prompt:cat，message.content:cat --v 6.0，这样，equals就判断不到
	private String containPrompt;
	private String promptEn;
	//针对blend指令
	private String finalPromptEn;
	private String messageId;
	private String nonce;
	//针对uv指令，监听过滤用
	private String description;


	@Override
	public boolean test(Task task) {
		if (task == null) {
			return false;
		}
		if (ObjUtil.isNotNull(this.taskId) && !this.taskId.equals(task.getId())) {
			return false;
		}
		if (this.statusSet != null && !this.statusSet.isEmpty() && !this.statusSet.contains(task.getStatus())) {
			return false;
		}
		if (this.actionSet != null && !this.actionSet.isEmpty() && !this.actionSet.contains(task.getAction())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.prompt) && !this.prompt.equals(task.getPrompt())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.containPrompt) && !this.containPrompt.contains(task.getPromptEn())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.promptEn) && !this.promptEn.equals(task.getPromptEn())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.finalPromptEn) && !this.finalPromptEn.equals(task.getFinalPromptEn())) {
			return false;
		}

		if (CharSequenceUtil.isNotBlank(this.messageId) && !this.messageId.equals(task.getMessageId())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.nonce) && !this.nonce.equals(task.getNonce())) {
			return false;
		}
		if (CharSequenceUtil.isNotBlank(this.description) && !this.description.equals(task.getDescription())) {
			return false;
		}

		return true;
	}

}
