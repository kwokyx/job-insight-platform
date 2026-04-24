package com.career.platform.auth.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class AuthMailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${career.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${career.mail.from:${spring.mail.username:}}")
    private String mailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public AuthMailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public boolean isMailAvailable() {
        return mailEnabled
                && StringUtils.hasText(mailHost)
                && StringUtils.hasText(mailFrom)
                && StringUtils.hasText(mailUsername)
                && mailSenderProvider.getIfAvailable() != null;
    }

    public void sendPasswordResetCode(String to, String username, String code, Duration ttl) {
        if (!StringUtils.hasText(to)) {
            throw new IllegalArgumentException("缺少收件邮箱");
        }
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("缺少验证码");
        }
        if (!isMailAvailable()) {
            throw new IllegalStateException("邮件服务未开启");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new IllegalStateException("JavaMailSender 不可用");
        }

        String displayName = StringUtils.hasText(username) ? username.trim() : "用户";
        long minutes = Math.max(1L, ttl == null ? 10L : ttl.toMinutes());

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(to.trim());
        message.setSubject("职业能力平台密码找回验证码");
        message.setText(new StringBuilder()
                .append("您好，").append(displayName).append("：\n\n")
                .append("您正在进行密码找回操作，本次验证码为：").append(code).append("\n")
                .append("验证码 ").append(minutes).append(" 分钟内有效，仅可使用一次。\n")
                .append("如非本人操作，请忽略此邮件并尽快检查账号安全。\n\n")
                .append("此邮件由系统自动发送，请勿直接回复。")
                .toString());
        mailSender.send(message);
    }
}
