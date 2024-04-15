package com.mj.utils;

import cn.hutool.core.text.CharSequenceUtil;
import com.mj.entity.ContentParseData;
import com.mj.enums.TaskAction;
import eu.maxschuster.dataurl.DataUrl;
import eu.maxschuster.dataurl.DataUrlSerializer;
import eu.maxschuster.dataurl.IDataUrlSerializer;
import lombok.experimental.UtilityClass;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mj.enums.TaskAction.*;


@UtilityClass
public class ConvertUtils {
	/**
	 * content正则匹配prompt和进度.
	 */
	public static final String CONTENT_REGEX = ".*?\\*\\*(.*?)\\*\\*.+<@\\d+> \\((.*?)\\)";
	/**
	 * 此正则可识别具体action，适用于如下指令：
	 * Upscaled (Creative) : **Pure Chinese female singer on campus, singing in the bar** - Upscaled (Creative) by <@426580409665716235> (fast)
	 * Upscaled (Subtle) : **Pure Chinese female singer on campus, singing in the bar** - Upscaled (Subtle) by <@426580409665716235> (fast)
	 * Zoom Out : **Pure Chinese female singer on campus, singing in the bar** - Zoom Out by <@426580409665716235> (fast)
	 * uv-Upscale : **Pure Chinese female singer on campus, singing in the bar** - Image #1 <@426580409665716235>
	 * uv-Variation : **Pure Chinese female singer on campus, singing in the bar** - Variations (Strong) by <@426580409665716235> (fast)
	 * Vary (Subtle) : **Pure Chinese female singer on campus, singing in the bar** - Variations (Subtle) by <@426580409665716235> (fast)
	 * Vary (Strong) : **Pure Chinese female singer on campus, singing in the bar** - Variations (Strong) by <@426580409665716235> (fast)
	 * 向左：**Chinese rock singers swing together --ar 3:2** - Pan Left by <@426580409665716235> (fast)
	 * 向上：**Chinese rock singers swing together --ar 2:3** - Pan Up by <@426580409665716235> (fast)
	 * Vary Region：**Lou Teeth Smile --v 6.0** - Variations (Region) by <@426580409665716235> (relaxed)
	 */
	public static final String CONTENT_REGEX_ACTION = "\\*\\*(.*?)\\*\\* - (.*?) by <@(\\d+)> \\((.*?)\\)";

	private static final Pattern MJ_U_CONTENT_REGEX_PATTERN = Pattern.compile("\\*\\*(.*?)\\*\\* - Image #(\\d) <@(\\d+)>");

	public static ContentParseData parseContent(String content) {
		return parseContent(content, CONTENT_REGEX);
	}

	public static ContentParseData parseContent(String content, String regex) {
		if (CharSequenceUtil.isBlank(content)) {
			return null;
		}
		Matcher matcher = Pattern.compile(regex).matcher(content);
		if (!matcher.find()) {
			return null;
		}
		ContentParseData parseData = new ContentParseData();
		parseData.setPrompt(matcher.group(1));
		parseData.setStatus(matcher.group(2));
		return parseData;
	}
	public static ContentParseData parseContentAction(String content) {
		if (CharSequenceUtil.isBlank(content)) {
			return null;
		}
		Matcher matcher = Pattern.compile(CONTENT_REGEX_ACTION).matcher(content);
		if (!matcher.find()) {
			return matchUpscaleContent(content);
		}
		ContentParseData parseData = new ContentParseData();
		parseData.setPrompt(matcher.group(1));
		String matchAction = matcher.group(2);
		if (matchAction.contains("Variations (Strong)")) {
			//指令v 或者 指令Vary(strong)，监听反馈都是一样的，无法区分，先写成这个，后面在runningtask中判断
			parseData.setAction(TaskAction.VARIATION);
		} else if (matchAction.contains("Upscaled (Creative)")) {
			//Upscaled (Creative)
			parseData.setAction(TaskAction.UPSCALE_CREATIVE);
		} else if (matchAction.contains("Upscaled (Subtle)")) {
			//Upscaled (Subtle)
			parseData.setAction(UPSCALE_SUBTLE);
		} else if (matchAction.contains("Zoom Out")) {
			//Zoom Out, 先默认是zoom out 1.5x，后续查找为空再改成 zoom out 2x
			parseData.setAction(OUTPAINT15X);
		} else if (matchAction.contains("Variations (Subtle)")) {
			//Vary (Subtle)
			parseData.setAction(VARY_SUBTLE);
		} else if (matchAction.contains("Pan Left")) {
			//向左
			parseData.setAction(DIRECTION_LEFT);
		} else if (matchAction.contains("Pan Right")) {
			//向右
			parseData.setAction(DIRECTION_RIGHT);
		} else if (matchAction.contains("Pan Up")) {
			//向上
			parseData.setAction(DIRECTION_UP);
		} else if (matchAction.contains("Pan Down")) {
			//向下
			parseData.setAction(DIRECTION_DOWN);
		} else if (matchAction.contains("Variations (Region)")) {
			//向下
			parseData.setAction(REGION);
		} else {
			//u
			parseData.setAction(TaskAction.UPSCALE);
		}
		parseData.setStatus(matcher.group(4));
		return parseData;
	}
	private static ContentParseData matchUpscaleContent(String content) {
		Matcher matcher = MJ_U_CONTENT_REGEX_PATTERN.matcher(content);
		if (!matcher.find()) {
			return null;
		}
		ContentParseData parseData = new ContentParseData();
		parseData.setPrompt(matcher.group(1));
		parseData.setAction(TaskAction.UPSCALE);
		parseData.setIndex(Integer.valueOf(matcher.group(2)));
		return parseData;
	}

	public static List<DataUrl> convertBase64Array(List<String> base64Array) throws MalformedURLException {
		if (base64Array == null || base64Array.isEmpty()) {
			return Collections.emptyList();
		}
		IDataUrlSerializer serializer = new DataUrlSerializer();
		List<DataUrl> dataUrlList = new ArrayList<>();
		for (String base64 : base64Array) {
			DataUrl dataUrl = serializer.unserialize(base64);
			dataUrlList.add(dataUrl);
		}
		return dataUrlList;
	}

	/*public static TaskChangeParams convertChangeParams(String content) {
		List<String> split = CharSequenceUtil.split(content, " ");
		if (split.size() != 2) {
			return null;
		}
		String action = split.get(1).toLowerCase();
		TaskChangeParams changeParams = new TaskChangeParams();
		changeParams.setId(split.get(0));
		if (action.charAt(0) == 'u') {
			changeParams.setAction(TaskAction.UPSCALE);
		} else if (action.charAt(0) == 'v') {
			changeParams.setAction(TaskAction.VARIATION);
		} else if (action.equals("r")) {
			changeParams.setAction(TaskAction.REROLL);
			return changeParams;
		} else {
			return null;
		}
		try {
			int index = Integer.parseInt(action.substring(1, 2));
			if (index < 1 || index > 4) {
				return null;
			}
			changeParams.setIndex(index);
		} catch (Exception e) {
			return null;
		}
		return changeParams;
	}*/

}
