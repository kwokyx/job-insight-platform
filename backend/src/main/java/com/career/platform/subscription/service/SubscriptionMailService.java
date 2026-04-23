package com.career.platform.subscription.service;

import com.career.platform.job.entity.JobPosting;
import com.career.platform.report.entity.AnalysisReport;
import com.career.platform.subscription.entity.UserSubscription;
import com.career.platform.system.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class SubscriptionMailService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionMailService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Map<String, String> REPORT_TYPE_LABELS = new LinkedHashMap<>();

    static {
        REPORT_TYPE_LABELS.put("COMPREHENSIVE", "综合分析");
        REPORT_TYPE_LABELS.put("JOB_SEEKING", "求职分析");
        REPORT_TYPE_LABELS.put("SKILL_GAP", "技能缺口分析");
        REPORT_TYPE_LABELS.put("SUPPLY_DEMAND", "供需分析");
        REPORT_TYPE_LABELS.put("TEACHING_ADVICE", "教学建议");
        REPORT_TYPE_LABELS.put("OPERATIONS", "运营分析");
        REPORT_TYPE_LABELS.put("SALARY", "薪资分析");
        REPORT_TYPE_LABELS.put("INDUSTRY", "行业分析");
        REPORT_TYPE_LABELS.put("SKILL", "技能分析");
    }

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${career.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${career.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public SubscriptionMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isMailAvailable() {
        return mailEnabled
                && StringUtils.hasText(mailHost)
                && StringUtils.hasText(mailFrom)
                && StringUtils.hasText(mailUsername)
                && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendJobMatches(SysUser user, UserSubscription subscription, List<JobPosting> matches) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalStateException("Current user has no bound email address");
        }
        if (!isMailAvailable()) {
            throw new IllegalStateException("Mail service is not enabled");
        }
        if (matches == null || matches.isEmpty()) {
            log.info("Skip email push for user {} because no job matches were found", user.getId());
            return;
        }

        sendPlainTextMail(user.getEmail().trim(), buildJobSubject(matches.size()), buildJobBody(user, subscription, matches));
        log.info("Subscription email sent to user {} <{}>, matched jobs: {}",
                user.getId(), user.getEmail(), matches.size());
    }

    public void sendReportReady(SysUser user, AnalysisReport report) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalStateException("Current user has no bound email address");
        }
        if (report == null || report.getId() == null) {
            throw new IllegalArgumentException("Report is required");
        }
        if (!isMailAvailable()) {
            throw new IllegalStateException("Mail service is not enabled");
        }

        sendPlainTextMail(user.getEmail().trim(), buildReportSubject(report), buildReportBody(user, report));
        log.info("Report ready email sent to user {} <{}>, reportId: {}",
                user.getId(), user.getEmail(), report.getId());
    }

    private void sendPlainTextMail(String to, String subject, String text) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender is unavailable");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private String buildJobSubject(int matchCount) {
        return String.format("职业能力平台岗位订阅提醒：新增 %d 条匹配岗位", matchCount);
    }

    private String buildJobBody(SysUser user, UserSubscription subscription, List<JobPosting> matches) {
        StringBuilder builder = new StringBuilder();
        builder.append("你好，")
                .append(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .append("：\n\n")
                .append("你订阅的岗位有新的匹配结果，已为你整理如下。\n")
                .append("订阅类型：")
                .append(StringUtils.hasText(subscription.getSubscriptionType()) ? subscription.getSubscriptionType() : "JOB_PUSH")
                .append("\n")
                .append("推送时间：")
                .append(LocalDateTime.now().format(TIME_FORMATTER))
                .append("\n");

        if (StringUtils.hasText(subscription.getFilterConfig())) {
            builder.append("筛选条件：").append(subscription.getFilterConfig()).append("\n");
        }

        builder.append("\n本次命中岗位：\n");
        int index = 1;
        for (JobPosting job : matches) {
            builder.append(index++)
                    .append(". ")
                    .append(defaultText(job.getTitle(), "岗位名称待补充"))
                    .append("\n   公司：")
                    .append(defaultText(job.getCompanyName(), "企业名称待补充"))
                    .append("\n   城市：")
                    .append(defaultText(job.getCity(), "城市待补充"))
                    .append("\n   薪资：")
                    .append(defaultText(job.getSalaryText(), buildSalaryRange(job)))
                    .append("\n\n");
        }

        builder.append("请登录职业能力平台查看完整岗位详情，并根据意向及时投递。\n\n")
                .append("此邮件由系统自动发送，请勿直接回复。");
        return builder.toString();
    }

    private String buildReportSubject(AnalysisReport report) {
        return "职业能力平台分析报告已生成：" + defaultText(report.getReportName(), "未命名报告");
    }

    private String buildReportBody(SysUser user, AnalysisReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("你好，")
                .append(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .append("：\n\n")
                .append("你的分析报告已经生成完成，可前往报告中心查看和导出。\n")
                .append("报告名称：")
                .append(defaultText(report.getReportName(), "未命名报告"))
                .append("\n")
                .append("报告类型：")
                .append(resolveReportTypeLabel(report.getReportType()))
                .append("\n")
                .append("生成时间：")
                .append(report.getGeneratedAt() == null ? LocalDateTime.now().format(TIME_FORMATTER) : report.getGeneratedAt().format(TIME_FORMATTER))
                .append("\n");

        if (StringUtils.hasText(report.getDescription())) {
            builder.append("摘要：")
                    .append(report.getDescription().trim())
                    .append("\n");
        }

        builder.append("\n请登录职业能力平台，在“报告中心”中查看完整内容、历史版本和导出结果。\n\n")
                .append("此邮件由系统自动发送，请勿直接回复。");
        return builder.toString();
    }

    private String buildSalaryRange(JobPosting job) {
        if (job.getSalaryMin() == null && job.getSalaryMax() == null) {
            return "薪资待补充";
        }
        String min = job.getSalaryMin() == null ? "不限" : job.getSalaryMin().stripTrailingZeros().toPlainString() + "K";
        String max = job.getSalaryMax() == null ? "不限" : job.getSalaryMax().stripTrailingZeros().toPlainString() + "K";
        return min + " - " + max;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String resolveReportTypeLabel(String reportType) {
        if (!StringUtils.hasText(reportType)) {
            return "分析报告";
        }
        return REPORT_TYPE_LABELS.getOrDefault(reportType.trim().toUpperCase(), reportType.trim());
    }
}
