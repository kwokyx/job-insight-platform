package com.career.platform.report.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SensitiveDataMaskingService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+-]{1,64})@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1\\d{10})(?!\\d)");

    public Map<String, Object> maskReportData(Map<String, Object> source) {
        return maskMap(source);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> maskMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.put(key, maskMap((Map<String, Object>) value));
            } else if (value instanceof List) {
                result.put(key, maskList((List<?>) value));
            } else if (value instanceof String) {
                result.put(key, maskValue(key, (String) value));
            } else {
                result.put(key, value);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> maskList(List<?> source) {
        List<Object> result = new ArrayList<>();
        for (Object item : source) {
            if (item instanceof Map) {
                result.add(maskMap((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(maskList((List<?>) item));
            } else if (item instanceof String) {
                result.add(maskText((String) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private Object maskValue(String key, String value) {
        if (value == null) {
            return null;
        }
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (normalizedKey.contains("email")) {
            return maskEmail(value);
        }
        if (normalizedKey.contains("phone") || normalizedKey.contains("mobile")) {
            return maskPhone(value);
        }
        if (normalizedKey.contains("secret") || normalizedKey.contains("token") || normalizedKey.contains("authorization")) {
            return maskSecret(value);
        }
        return maskText(value);
    }

    private String maskText(String text) {
        return maskPhone(maskEmail(text));
    }

    private String maskEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text == null ? "" : text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String local = matcher.group(1);
            String domain = matcher.group(2);
            String maskedLocal = local.length() <= 2 ? local.charAt(0) + "*" : local.substring(0, 2) + "***";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskedLocal + "@" + domain));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text == null ? "" : text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String phone = matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(phone.substring(0, 3) + "****" + phone.substring(7)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskSecret(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 3);
    }
}
