package com.mj.enums;


public enum TaskAction {
	/**
	 * 生成图片.
	 */
	IMAGINE,
	/**
	 * uv-u 选中放大.
	 */
	UPSCALE,
	/**
	 * uv-v 选中其中的一张图，生成四张相似的.
	 */
	VARIATION,
	/**
	 * 重新执行.
	 */
	REROLL,
	/**
	 * 图转prompt.
	 */
	DESCRIBE,
	/**
	 * 多图混合.
	 */
	BLEND,

	/**
	 * 像素放大2倍
	 */
	UPSCALE_SUBTLE,

	/**
	 * 像素放大4倍
	 */
	UPSCALE_CREATIVE,

	/**
	 * Zoom Out 缩放1.5倍并补充细节生成四张图
	 */
	OUTPAINT15X,

	/**
	 * Zoom Out 缩放2倍并补充细节生成四张图
	 */
	OUTPAINT2X,

	/**
	 * 细微变化生成四张图
	 */
	VARY_SUBTLE,

	/**
	 * 强烈变化生成四张图
	 */
	VARY_STRONG,


	/**
	 * 往上延伸生成四张图
	 */
	DIRECTION_UP,
	/**
	 * 往下延伸生成四张图
	 */
	DIRECTION_DOWN,
	/**
	 * 往左延伸生成四张图
	 */
	DIRECTION_LEFT,
	/**
	 * 往右延伸生成四张图
	 */
	DIRECTION_RIGHT,
	/**
	 * Vary Region
	 */
	REGION;
}
