package com.career.platform.subscription.service;

import com.career.platform.job.entity.JobPosting;
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

@Service
public class SubscriptionMailService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionMailService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail().trim());
        message.setSubject(buildSubject(matches.size()));
        message.setText(buildBody(user, subscription, matches));

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender is unavailable");
        }
        mailSender.send(message);
        log.info("Subscription email sent to user {} <{}>, matched jobs: {}",
                user.getId(), user.getEmail(), matches.size());
    }

    private String buildSubject(int matchCount) {
        return String.format("职业能力平台岗位订阅提醒：为你匹配到 %d 条新岗位", matchCount);
    }

    private String buildBody(SysUser user, UserSubscription subscription, List<JobPosting> matches) {
        StringBuilder builder = new StringBuilder();
        builder.append("你好，")
                .append(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .append("：\n\n")
                .append("你的岗位订阅有新的匹配结果。\n")
                .append("订阅类型：")
                .append(StringUtils.hasText(subscription.getSubscriptionType()) ? subscription.getSubscriptionType() : "JOB_PUSH")
                .append("\n")
                .append("推送时间：")
                .append(LocalDateTime.now().format(TIME_FORMATTER))
                .append("\n");

        if (StringUtils.hasText(subscription.getFilterConfig())) {
            builder.append("筛选条件：").append(subscription.getFilterConfig()).append("\n");
        }

        builder.append("\n匹配岗位如下：\n");
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

        builder.append("请登录平台查看完整详情。");
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
}
