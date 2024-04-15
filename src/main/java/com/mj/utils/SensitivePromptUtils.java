package com.mj.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.mj.exception.SensitivePromptException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@Slf4j
public class SensitivePromptUtils {
	private static final String BANNED_WORDS_FILE_PATH = "classpath:banned-words.txt";
	private final List<String> BANNED_WORDS;

	static {
		List<String> lines;
		File file = null;
		try {
			file = ResourceUtils.getFile(BANNED_WORDS_FILE_PATH);
			lines = FileUtil.readLines(file, StandardCharsets.UTF_8);
		} catch (FileNotFoundException e) {
			log.info("获取敏感词文件失败");
			throw new RuntimeException("获取敏感词文件失败");
		}
		/*if (file.exists()) {
			lines = FileUtil.readLines(file, StandardCharsets.UTF_8);
		} else {
			var resource = SensitivePromptUtils.class.getResource("/banned-words.txt");
			lines = FileUtil.readLines(resource, StandardCharsets.UTF_8);
		}*/
		BANNED_WORDS = lines.stream().filter(CharSequenceUtil::isNotBlank).toList();
	}

	public static void checkSensitivePrompt(String promptEn) throws SensitivePromptException {
		String finalPromptEn = promptEn.toLowerCase(Locale.ENGLISH);
		for (String word : BANNED_WORDS) {
			Matcher matcher = Pattern.compile("\\b" + word + "\\b").matcher(finalPromptEn);
			if (matcher.find()) {
				int index = CharSequenceUtil.indexOfIgnoreCase(promptEn, word);
				throw new SensitivePromptException(promptEn.substring(index, index + word.length()));
			}
		}
	}

}
