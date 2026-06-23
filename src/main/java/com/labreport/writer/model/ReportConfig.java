package com.labreport.writer.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * ReportConfig - 报告生成配置（不可变）。
 * <p>
 * 包含字体、行间距、输出格式、证据模式等设置。
 * 使用 Java 17 record 确保不可变性。
 * </p>
 */
@JsonSerialize
@JsonDeserialize
public record ReportConfig(

    /** 中文字体名称，默认"宋体" */
    @JsonProperty(defaultValue = "宋体") String cnFont,

    /** 英文字体名称，默认"Times New Roman" */
    @JsonProperty(defaultValue = "Times New Roman") String enFont,

    /** 正文字号（半磅），12pt = 24 */
    @JsonProperty(defaultValue = "24") int fontSizeHalfPt,

    /** 行间距（1/240行），1.5 = 360 */
    @JsonProperty(defaultValue = "360") int lineSpacing,

    /** 输出格式: docx / pdf */
    @JsonProperty(defaultValue = "docx") String outputFormat,

    /** 证据模式: auto / real-only / allow-simulated */
    @JsonProperty(defaultValue = "auto") String evidenceMode,

    /** 是否自动生成UML图 */
    @JsonProperty(defaultValue = "true") boolean autoGenerateUml,

    /** 是否启用验证 */
    @JsonProperty(defaultValue = "true") boolean enableValidation,

    /** 输出目录 */
    @JsonProperty(defaultValue = "") String outputDir

) {
    /** 紧凑构造器：提供默认值 */
    public ReportConfig {
        if (cnFont == null || cnFont.isBlank()) cnFont = "宋体";
        if (enFont == null || enFont.isBlank()) enFont = "Times New Roman";
        if (fontSizeHalfPt <= 0) fontSizeHalfPt = 24;
        if (lineSpacing <= 0) lineSpacing = 360;
        if (outputFormat == null || outputFormat.isBlank()) outputFormat = "docx";
        if (evidenceMode == null || evidenceMode.isBlank()) evidenceMode = "auto";
        if (outputDir == null) outputDir = "";
    }

    /** 便捷工厂：全部默认值 */
    public static ReportConfig defaults() {
        return new ReportConfig("宋体", "Times New Roman", 24, 360,
            "docx", "auto", true, true, "");
    }
}
