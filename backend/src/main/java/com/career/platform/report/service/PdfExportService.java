package com.career.platform.report.service;

import com.lowagie.text.pdf.BaseFont;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final TemplateEngine templateEngine;

    public byte[] generatePdf(String reportName, String reportType,
                              Map<String, Object> analysisData, String summary) {
        try {
            Context ctx = new Context();
            ctx.setVariable("reportName", reportName);
            ctx.setVariable("reportType", reportType);
            ctx.setVariable("data", analysisData);
            ctx.setVariable("summary", summary);
            ctx.setVariable("generatedAt", LocalDateTime.now());
            ctx.setVariable("model", buildVisualModel(analysisData, reportType));

            String html = templateEngine.process("report_template", ctx);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            configureFonts(renderer);
            renderer.setDocumentFromString(html, null);
            renderer.layout();
            renderer.createPDF(baos);
            renderer.finishPDF();

            log.info("PDF generated successfully: {} ({} bytes)", reportName, baos.size());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed", e);
            throw new RuntimeException("PDF 导出失败: " + e.getMessage(), e);
        }
    }

    private void configureFonts(ITextRenderer renderer) {
        try {
            ITextFontResolver resolver = renderer.getFontResolver();
            
            // 尝试加载常规宋体 TTC
            String[] fontPaths = {
                "C:\\Windows\\Fonts\\simsun.ttc",
                "C:\\Windows\\Fonts\\simsun.ttf",
                "C:\\Windows\\Fonts\\msyh.ttc",    // 微软雅黑
                "C:\\Windows\\Fonts\\msyh.ttf"
            };

            boolean fontFound = false;
            for (String path : fontPaths) {
                File fontFile = new File(path);
                if (fontFile.exists()) {
                    String finalPath = path.toLowerCase().endsWith(".ttc") ? path + ",0" : path;
                    resolver.addFont(finalPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    log.info("Successfully registered PDF font: {}", path);
                    fontFound = true;
                }
            }
            
            if (!fontFound) {
                log.warn("No suitable Chinese fonts found for PDF generation!");
            }
        } catch (Exception e) {
            log.error("Failed to configure PDF font", e);
        }
    }

    private Map<String, Object> buildVisualModel(Map<String, Object> analysisData, String reportType) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("kpis", buildKpis(analysisData));
        model.put("barCharts", buildBarCharts(analysisData));
        model.put("salaryTrend", buildTrendPoints(analysisData));
        model.put("keyFindings", buildKeyFindings(analysisData, reportType));
        return model;
    }

    private List<Map<String, Object>> buildKpis(Map<String, Object> analysisData) {
        Map<String, Object> overview = asMap(analysisData.get("overview"));
        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = pickFirstAvailableList(analysisData, "topCities", "salaryByCity");

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpi("样本岗位数", formatNumber(overview.get("totalJobs"))));
        kpis.add(kpi("平均薪资下限", formatSalary(overview.get("avgSalaryMin"))));
        kpis.add(kpi("平均薪资上限", formatSalary(overview.get("avgSalaryMax"))));

        if (!topSkills.isEmpty()) {
            String skill = asText(topSkills.get(0).get("skill"));
            String count = formatNumber(topSkills.get(0).get("count"));
            kpis.add(kpi("最热技能", skill + "（" + count + "）"));
        }

        if (!topCities.isEmpty()) {
            Map<String, Object> city = topCities.get(0);
            kpis.add(kpi("核心城市", asText(city.get("city")) + "（" + formatNumber(city.get("count")) + "）"));
        }
        return kpis;
    }

    private List<Map<String, Object>> buildBarCharts(Map<String, Object> analysisData) {
        List<Map<String, Object>> charts = new ArrayList<>();
        addChart(charts, "技能需求Top10", "人才市场最重视的能力标签", asList(analysisData.get("topSkills")), "skill", "count", 10);

        List<Map<String, Object>> cityRows = pickFirstAvailableList(analysisData, "topCities", "salaryByCity");
        addChart(charts, "城市需求分布", "岗位数量在城市维度的集中度", cityRows, "city", "count", 8);

        List<Map<String, Object>> industryRows = pickFirstAvailableList(analysisData, "topIndustries", "industries", "salaryByIndustry", "skillsByIndustry");
        addChart(charts, "行业分布", "需求主要集中行业", industryRows, "industry", "count", 8);

        List<Map<String, Object>> educationRows = pickFirstAvailableList(analysisData, "educationDist", "salaryByEducation");
        addChart(charts, "学历要求分布", "企业对学历的偏好结构", educationRows, "education", "count", 8);

        List<Map<String, Object>> experienceRows = pickFirstAvailableList(analysisData, "experienceDist", "salaryByExperience");
        addChart(charts, "经验要求分布", "不同经验层级的岗位占比", experienceRows, "experience", "count", 8);

        return charts;
    }

    private void addChart(
            List<Map<String, Object>> charts,
            String title,
            String subtitle,
            List<Map<String, Object>> rows,
            String labelKey,
            String valueKey,
            int limit
    ) {
        List<Map<String, Object>> normalized = normalizeBarItems(rows, labelKey, valueKey, limit);
        if (normalized.isEmpty()) {
            return;
        }

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("title", title);
        chart.put("subtitle", subtitle);
        chart.put("items", normalized);
        charts.add(chart);
    }

    private List<Map<String, Object>> buildTrendPoints(Map<String, Object> analysisData) {
        List<Map<String, Object>> trend = asList(analysisData.get("salaryTrend"));
        if (trend.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> points = new ArrayList<>();
        double max = 0D;
        for (Map<String, Object> row : trend) {
            max = Math.max(max, toDouble(row.get("avgSalaryMin")));
        }

        int start = Math.max(0, trend.size() - 10);
        for (int i = start; i < trend.size(); i++) {
            Map<String, Object> row = trend.get(i);
            double v = toDouble(row.get("avgSalaryMin"));
            int pct = max <= 0 ? 0 : (int) Math.round((v / max) * 100);
            if (v > 0 && pct < 4) {
                pct = 4;
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("period", asText(row.get("period")));
            point.put("avgMin", formatSalary(v));
            point.put("avgMax", formatSalary(row.get("avgSalaryMax")));
            point.put("jobCount", formatNumber(row.get("jobCount")));
            point.put("barPercent", pct);
            points.add(point);
        }
        return points;
    }

    private List<Map<String, Object>> buildKeyFindings(Map<String, Object> analysisData, String reportType) {
        List<Map<String, Object>> findings = new ArrayList<>();

        List<Map<String, Object>> chartInsights = toTextBullets(analysisData.get("chartInsights"));
        findings.addAll(chartInsights);

        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        if (topSkills.size() >= 3) {
            double top1 = toDouble(topSkills.get(0).get("count"));
            double top3 = toDouble(topSkills.get(2).get("count"));
            if (top1 > 0 && top3 > 0) {
                double ratio = top1 / top3;
                findings.add(finding(
                        "技能集中度",
                        "Top1 技能需求强度约为 Top3 的 " + String.format("%.2f", ratio) + " 倍，建议优先覆盖头部技能。"
                ));
            }
        }

        List<Map<String, Object>> trend = asList(analysisData.get("salaryTrend"));
        if (trend.size() >= 2) {
            double first = toDouble(trend.get(0).get("avgSalaryMin"));
            double last = toDouble(trend.get(trend.size() - 1).get("avgSalaryMin"));
            if (first > 0 && last > 0) {
                double change = (last - first) / first * 100;
                findings.add(finding(
                        "薪资走势",
                        "报告周期内平均薪资下限变化 " + String.format("%+.1f", change) + "%，当前报告类型为 " + asText(reportType) + "。"
                ));
            }
        }

        List<Map<String, Object>> recommendations = toTextBullets(analysisData.get("recommendations"));
        findings.addAll(recommendations);

        if (findings.isEmpty()) {
            findings.add(finding("结论", "当前样本不足以形成稳定结论，建议扩大时间范围或增大样本量后再次导出。"));
        }
        return findings;
    }

    private List<Map<String, Object>> toTextBullets(Object source) {
        if (!(source instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rows = (List<?>) source;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows) {
            if (row == null) {
                continue;
            }
            String text = asText(row);
            if (text.isEmpty()) {
                continue;
            }
            result.add(finding("洞察", text));
        }
        return result;
    }

    private List<Map<String, Object>> normalizeBarItems(
            List<Map<String, Object>> rows,
            String labelKey,
            String valueKey,
            int limit
    ) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> capped = new ArrayList<>();
        int maxCount = Math.min(Math.max(limit, 1), rows.size());
        for (int i = 0; i < maxCount; i++) {
            capped.add(rows.get(i));
        }

        double max = 0D;
        for (Map<String, Object> row : capped) {
            max = Math.max(max, toDouble(row.get(valueKey)));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : capped) {
            String label = asText(row.get(labelKey));
            double value = toDouble(row.get(valueKey));
            if (label.isEmpty() || value <= 0) {
                continue;
            }
            int pct = max <= 0 ? 0 : (int) Math.round((value / max) * 100);
            if (pct > 0 && pct < 4) {
                pct = 4;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("value", formatNumber(value));
            item.put("percent", pct);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> pickFirstAvailableList(Map<String, Object> analysisData, String... keys) {
        if (analysisData == null || keys == null) {
            return Collections.emptyList();
        }
        for (String key : keys) {
            List<Map<String, Object>> value = asList(analysisData.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> list = (List<?>) value;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private Map<String, Object> kpi(String label, String value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("value", value);
        return item;
    }

    private Map<String, Object> finding(String title, String detail) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", title);
        item.put("detail", detail);
        return item;
    }

    private String formatSalary(Object value) {
        double number = toDouble(value);
        if (number <= 0) {
            return "N/A";
        }
        return String.format("%.2fK", number);
    }

    private String formatNumber(Object value) {
        if (value == null) {
            return "N/A";
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Math.abs(number - Math.rint(number)) < 0.0001D) {
                return String.format("%.0f", number);
            }
            return String.format("%.2f", number);
        }
        return Objects.toString(value, "N/A");
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0D;
        }
    }
}
