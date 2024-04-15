package com.mj.entity;

import com.mj.enums.BlendDimensions;
import lombok.Data;

import java.util.List;


@Data
public class ExecuteBlendDTO{
	/**
	 * 图片base64数组
	 */
	private List<String> base64Array;
	/**
	 * 比例: PORTRAIT(2:3); SQUARE(1:1); LANDSCAPE(3:2)
	 */
	private BlendDimensions dimensions = BlendDimensions.SQUARE;

}
