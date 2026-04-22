package com.career.platform.report.service;

import org.springframework.util.StringUtils;

public enum ReportFailureReason {
    INPUT_MISSING,
    DATA_SOURCE_UNAVAILABLE,
    EXPORT_FAILED,
    MODEL_UNAVAILABLE,
    SYSTEM_ERROR;

    public static ReportFailureReason detect(String errorMessage) {
        String text = StringUtils.hasText(errorMessage) ? errorMessage.toLowerCase() : "";
        if (text.contains("upload") || text.contains("课程") || text.contains("教学大纲") || text.contains("学生情况")) {
            return INPUT_MISSING;
        }
        if (text.contains("mysql") || text.contains("redis") || text.contains("data source") || text.contains("数据库")) {
            return DATA_SOURCE_UNAVAILABLE;
        }
        if (text.contains("export") || text.contains("pdf") || text.contains("导出")) {
            return EXPORT_FAILED;
        }
        if (text.contains("llm") || text.contains("model") || text.contains("ai")) {
            return MODEL_UNAVAILABLE;
        }
        return SYSTEM_ERROR;
    }
}
