package com.career.platform.report.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final TemplateEngine templateEngine;

    public byte[] generatePdf(String reportName, String reportType, Map<String, Object> analysisData, String summary) {
        try {
            Context ctx = new Context();
            ctx.setVariable("reportName", reportName);
            ctx.setVariable("reportType", reportType);
            ctx.setVariable("data", analysisData);
            ctx.setVariable("summary", summary);
            ctx.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
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
            log.error("Template PDF generation failed, fallback to simple PDF", e);
            return generateSimplePdf(reportName, reportType, analysisData, summary);
        }
    }

    private void configureFonts(ITextRenderer renderer) {
        try {
            ITextFontResolver resolver = renderer.getFontResolver();
            List<String> candidateFonts = new ArrayList<>();
            Collections.addAll(candidateFonts,
                    "C:\\Windows\\Fonts\\msyh.ttc",
                    "C:\\Windows\\Fonts\\msyhbd.ttc",
                    "C:\\Windows\\Fonts\\simsun.ttc",
                    "C:\\Windows\\Fonts\\simhei.ttf",
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
                    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc",
                    "/usr/share/fonts/opentype/noto/NotoSerifCJK-Bold.ttc",
                    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
                    "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"
            );

            boolean found = false;
            for (String path : candidateFonts) {
                File file = new File(path);
                if (!file.exists()) {
                    continue;
                }
                if (path.toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    for (int i = 0; i < 4; i++) {
                        try {
                            resolver.addFont(path + "," + i, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                            found = true;
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    resolver.addFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    found = true;
                }
                log.info("Registered PDF font candidate: {}", path);
            }

            if (!found) {
                log.warn("No suitable Chinese font found for PDF rendering.");
            }
        } catch (Exception e) {
            log.error("Failed to configure PDF font", e);
        }
    }

    private byte[] generateSimplePdf(String reportName, String reportType, Map<String, Object> analysisData, String summary) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = buildFallbackFont(16, true);
            Font bodyFont = buildFallbackFont(10, false);

            document.add(new Paragraph(Objects.toString(reportName, "Analysis Report"), titleFont));
            document.add(new Paragraph("Report Type: " + Objects.toString(reportType, "GENERAL"), bodyFont));
            document.add(new Paragraph("Generated At: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), bodyFont));
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph(Objects.toString(summary, "No summary available."), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            addFallbackSection(document, "Overview", analysisData.get("overview"), titleFont, bodyFont);
            addFallbackSection(document, "Recommendations", analysisData.get("recommendations"), titleFont, bodyFont);
            addFallbackSection(document, "Action Plan", analysisData.get("actionPlan"), titleFont, bodyFont);
            addFallbackSection(document, "Chart Insights", analysisData.get("chartInsights"), titleFont, bodyFont);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            log.error("Simple PDF generation failed, fallback to minimal PDF", ex);
            return generateMinimalPdf(reportName, reportType, summary);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    private void addFallbackSection(Document document, String title, Object content, Font titleFont, Font bodyFont) throws Exception {
        if (content == null) {
            return;
        }
        document.add(new Paragraph(title, titleFont));
        document.add(new Paragraph(Objects.toString(content, "-"), bodyFont));
        document.add(new Paragraph(" ", bodyFont));
    }

    private Font buildFallbackFont(float size, boolean bold) {
        BaseFont baseFont = resolveFallbackBaseFont();
        int style = bold ? Font.BOLD : Font.NORMAL;
        return baseFont == null ? new Font(Font.HELVETICA, size, style) : new Font(baseFont, size, style);
    }

    private byte[] generateMinimalPdf(String reportName, String reportType, String summary) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, baos);
            document.open();
            Font titleFont = buildFallbackFont(14, true);
            Font bodyFont = buildFallbackFont(10, false);
            document.add(new Paragraph(Objects.toString(reportName, "Analysis Report"), titleFont));
            document.add(new Paragraph(Objects.toString(reportType, "GENERAL"), bodyFont));
            document.add(new Paragraph(Objects.toString(summary, "Report generated with minimal fallback content."), bodyFont));
        } catch (Exception ignored) {
            log.error("Minimal PDF generation also failed");
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
        return baos.toByteArray();
    }

    private BaseFont resolveFallbackBaseFont() {
        List<String> candidates = new ArrayList<>();
        Collections.addAll(candidates,
                "C:\\Windows\\Fonts\\msyh.ttc,0",
                "C:\\Windows\\Fonts\\simsun.ttc,0",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,0",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttf"
        );
        for (String path : candidates) {
            try {
                return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Map<String, Object> buildVisualModel(Map<String, Object> analysisData, String reportType) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("kpis", buildKpis(analysisData));
        model.put("trendSummary", buildTrendSummary(analysisData));
        model.put("barCharts", buildBarCharts(analysisData));
        model.put("salaryTrend", buildTrendPoints(analysisData));
        model.put("distributionCards", buildDistributionCards(analysisData));
        model.put("keyFindings", buildKeyFindings(analysisData, reportType));
        return model;
    }

    private List<Map<String, Object>> buildKpis(Map<String, Object> analysisData) {
        Map<String, Object> overview = asMap(analysisData.get("overview"));
        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        List<Map<String, Object>> topCities = pickFirstAvailableList(analysisData, "topCities", "salaryByCity");

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpi("岗位样本量", formatNumber(overview.get("totalJobs"))));
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

    private List<Map<String, Object>> buildTrendSummary(Map<String, Object> analysisData) {
        List<Map<String, Object>> trend = asList(analysisData.get("salaryTrend"));
        if (trend.isEmpty()) {
            return Collections.emptyList();
        }

        int start = Math.max(0, trend.size() - 8);
        List<Map<String, Object>> recent = trend.subList(start, trend.size());
        Map<String, Object> latest = recent.get(recent.size() - 1);
        Map<String, Object> first = recent.get(0);

        double firstMin = toDouble(first.get("avgSalaryMin"));
        double latestMin = toDouble(latest.get("avgSalaryMin"));
        double latestMax = toDouble(latest.get("avgSalaryMax"));
        double latestCount = toDouble(latest.get("jobCount"));
        String change = "N/A";
        if (firstMin > 0 && latestMin > 0) {
            change = String.format(Locale.US, "%+.1f%%", (latestMin - firstMin) / firstMin * 100);
        }

        List<Map<String, Object>> summaryCards = new ArrayList<>();
        summaryCards.add(kpi("最新薪资下限", formatSalary(latestMin)));
        summaryCards.add(kpi("最新薪资上限", formatSalary(latestMax)));
        summaryCards.add(kpi("最新岗位样本", formatNumber(latestCount)));
        summaryCards.add(kpi("阶段变化", change));
        return summaryCards;
    }

    private List<Map<String, Object>> buildBarCharts(Map<String, Object> analysisData) {
        List<Map<String, Object>> charts = new ArrayList<>();
        addChart(charts, "热门技能需求 Top10", "当前市场中最集中的高频技能标签", asList(analysisData.get("topSkills")), "skill", "count", 10);
        addChart(charts, "城市需求分布", "岗位需求主要集中在哪些城市", pickFirstAvailableList(analysisData, "topCities", "salaryByCity"), "city", "count", 8);
        addChart(charts, "岗位方向分布", "当前更值得优先跟进的岗位方向", pickFirstAvailableList(analysisData, "topIndustries", "industries", "salaryByIndustry"), "industry", "count", 8);
        addChart(charts, "学历要求分布", "企业对学历门槛的结构偏好", pickFirstAvailableList(analysisData, "educationDist", "salaryByEducation"), "education", "count", 8);
        addChart(charts, "经验要求分布", "不同经验层级岗位的分布情况", pickFirstAvailableList(analysisData, "experienceDist", "salaryByExperience"), "experience", "count", 8);
        return charts;
    }

    private void addChart(List<Map<String, Object>> charts, String title, String subtitle, List<Map<String, Object>> rows,
                          String labelKey, String valueKey, int limit) {
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
            double current = toDouble(row.get("avgSalaryMin"));
            int percent = max <= 0 ? 0 : (int) Math.round((current / max) * 100);
            if (current > 0 && percent < 4) {
                percent = 4;
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("period", asText(row.get("period")));
            point.put("avgMin", formatSalary(current));
            point.put("avgMax", formatSalary(row.get("avgSalaryMax")));
            point.put("jobCount", formatNumber(row.get("jobCount")));
            point.put("barPercent", percent);
            points.add(point);
        }
        return points;
    }

    private List<Map<String, Object>> buildDistributionCards(Map<String, Object> analysisData) {
        List<Map<String, Object>> cards = new ArrayList<>();
        addDistributionCard(cards, "学历要求结构", "不同学历门槛对应的岗位占比", asList(analysisData.get("educationDist")), "education", "count");
        addDistributionCard(cards, "经验要求结构", "不同经验门槛对应的岗位占比", asList(analysisData.get("experienceDist")), "experience", "count");
        return cards;
    }

    private void addDistributionCard(List<Map<String, Object>> cards, String title, String subtitle, List<Map<String, Object>> rows,
                                     String labelKey, String valueKey) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        double total = 0D;
        for (Map<String, Object> row : rows) {
            total += toDouble(row.get(valueKey));
        }
        if (total <= 0) {
            return;
        }

        List<Map<String, Object>> items = new ArrayList<>();
        int limit = Math.min(rows.size(), 6);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            String label = asText(row.get(labelKey));
            double value = toDouble(row.get(valueKey));
            if (label.isEmpty() || value <= 0) {
                continue;
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("label", label);
            item.put("value", formatNumber(value));
            item.put("percent", (int) Math.max(6, Math.round(value / total * 100)));
            item.put("ratioText", String.format(Locale.US, "%.0f%%", value / total * 100));
            items.add(item);
        }

        if (items.isEmpty()) {
            return;
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("title", title);
        card.put("subtitle", subtitle);
        card.put("items", items);
        cards.add(card);
    }

    private List<Map<String, Object>> buildKeyFindings(Map<String, Object> analysisData, String reportType) {
        List<Map<String, Object>> findings = new ArrayList<>();
        findings.addAll(toTextBullets(analysisData.get("chartInsights"), "图表洞察"));
        findings.addAll(toTextBullets(analysisData.get("recommendations"), "建议"));

        List<Map<String, Object>> topSkills = asList(analysisData.get("topSkills"));
        if (topSkills.size() >= 3) {
            double top1 = toDouble(topSkills.get(0).get("count"));
            double top3 = toDouble(topSkills.get(2).get("count"));
            if (top1 > 0 && top3 > 0) {
                findings.add(finding("技能集中度",
                        "Top1 技能需求强度约为 Top3 的 " + String.format(Locale.US, "%.2f", top1 / top3) + " 倍，应优先覆盖头部能力。"));
            }
        }

        List<Map<String, Object>> trend = asList(analysisData.get("salaryTrend"));
        if (trend.size() >= 2) {
            double first = toDouble(trend.get(0).get("avgSalaryMin"));
            double last = toDouble(trend.get(trend.size() - 1).get("avgSalaryMin"));
            if (first > 0 && last > 0) {
                double change = (last - first) / first * 100;
                findings.add(finding("薪资趋势",
                        "在当前 " + asText(reportType) + " 报告周期内，平均薪资下限变化约为 "
                                + String.format(Locale.US, "%+.1f", change) + "%。"));
            }
        }

        if (findings.isEmpty()) {
            findings.add(finding("结论", "当前样本不足以形成稳定结论，建议扩大时间范围或样本量后再次导出。"));
        }
        return findings;
    }

    private List<Map<String, Object>> toTextBullets(Object source, String title) {
        if (!(source instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : (List<?>) source) {
            if (row == null) {
                continue;
            }
            String text = asText(row);
            if (!text.isEmpty()) {
                result.add(finding(title, text));
            }
        }
        return result;
    }

    private List<Map<String, Object>> normalizeBarItems(List<Map<String, Object>> rows, String labelKey, String valueKey, int limit) {
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
            item.put("valueText", formatNumber(value));
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
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
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
        return String.format(Locale.US, "%.2fK", number);
    }

    private String formatNumber(Object value) {
        if (value == null) {
            return "N/A";
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (Math.abs(number - Math.rint(number)) < 0.0001D) {
                return String.format(Locale.US, "%,.0f", number);
            }
            return String.format(Locale.US, "%,.2f", number);
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
