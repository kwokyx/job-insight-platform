package com.career.platform.crawl.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class CrawlRegionService {

    private static final Map<String, String> CODE_TO_NAME = new LinkedHashMap<String, String>();
    private static final Map<String, String> NAME_TO_CODE = new LinkedHashMap<String, String>();

    static {
        register("530", "\u5317\u4eac", "beijing", "bj");
        register("531", "\u5929\u6d25", "tianjin", "tj");
        register("538", "\u4e0a\u6d77", "shanghai", "sh");
        register("551", "\u91cd\u5e86", "chongqing", "cq");
        register("635", "\u5357\u4eac", "nanjing", "nj");
        register("653", "\u676d\u5dde", "hangzhou", "hz");
        register("736", "\u6b66\u6c49", "wuhan", "wh");
        register("763", "\u5e7f\u5dde", "guangzhou", "gz");
        register("765", "\u6df1\u5733", "shenzhen", "sz");
        register("801", "\u6210\u90fd", "chengdu", "cd");
    }

    private final JdbcTemplate jdbcTemplate;

    public CrawlRegionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> normalizeCityListForScheduler(List<String> values) {
        List<String> result = new ArrayList<String>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            String normalized = normalizeCityForScheduler(value);
            if (StringUtils.hasText(normalized) && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public String normalizeCityForScheduler(String value) {
        String text = repairText(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.matches("\\d+")) {
            return normalized;
        }

        String aliasCode = lookupCode(normalized);
        if (aliasCode != null) {
            return aliasCode;
        }

        String compact = stripRegionSuffix(normalized);
        aliasCode = lookupCode(compact);
        if (aliasCode != null) {
            return aliasCode;
        }

        String dbCode = queryRegionCode(normalized);
        if (StringUtils.hasText(dbCode)) {
            return dbCode;
        }
        dbCode = queryRegionCode(compact);
        if (StringUtils.hasText(dbCode)) {
            return dbCode;
        }
        return normalized;
    }

    public String resolveRegionDisplayName(String value) {
        String text = repairText(value);
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (!normalized.matches("\\d+")) {
            String compact = stripRegionSuffix(normalized);
            String aliasCode = lookupCode(compact);
            if (aliasCode != null) {
                return CODE_TO_NAME.getOrDefault(aliasCode, compact);
            }
            return normalized;
        }
        String dbName = queryRegionName(normalized);
        if (StringUtils.hasText(dbName)) {
            return repairText(dbName).trim();
        }
        return CODE_TO_NAME.getOrDefault(normalized, normalized);
    }

    private String queryRegionCode(String regionName) {
        try {
            List<String> rows = jdbcTemplate.query(
                    "SELECT region_code FROM dim_region " +
                            "WHERE status = 1 AND (" +
                            "region_name = ? OR region_name = CONCAT(?, '\u5e02') OR region_name = CONCAT(?, '\u7701') OR " +
                            "region_name = CONCAT(?, '\u81ea\u6cbb\u533a') OR region_name LIKE CONCAT(?, '%')) " +
                            "ORDER BY CASE region_level WHEN 3 THEN 0 WHEN 2 THEN 1 ELSE 2 END, sort_no ASC LIMIT 1",
                    (rs, rowNum) -> rs.getString(1),
                    regionName, regionName, regionName, regionName, regionName
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String queryRegionName(String regionCode) {
        try {
            List<String> rows = jdbcTemplate.query(
                    "SELECT region_name FROM dim_region WHERE region_code = ? AND status = 1 " +
                            "ORDER BY CASE region_level WHEN 3 THEN 0 WHEN 2 THEN 1 ELSE 2 END, sort_no ASC LIMIT 1",
                    (rs, rowNum) -> rs.getString(1),
                    regionCode
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripRegionSuffix(String value) {
        String result = value;
        result = result.replace("\u7701", "");
        result = result.replace("\u5e02", "");
        result = result.replace("\u81ea\u6cbb\u533a", "");
        result = result.replace("\u7279\u522b\u884c\u653f\u533a", "");
        return result.trim();
    }

    private String lookupCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return NAME_TO_CODE.get(value.trim().toLowerCase(Locale.ROOT));
    }

    private String repairText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return countCjk(repaired) > countCjk(value) ? repaired : value;
    }

    private int countCjk(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                count++;
            }
        }
        return count;
    }

    private static void register(String code, String name, String... aliases) {
        CODE_TO_NAME.put(code, name);
        NAME_TO_CODE.put(name.toLowerCase(Locale.ROOT), code);
        NAME_TO_CODE.put((name + "\u5e02").toLowerCase(Locale.ROOT), code);
        for (String alias : aliases) {
            NAME_TO_CODE.put(alias.toLowerCase(Locale.ROOT), code);
        }
    }
}
