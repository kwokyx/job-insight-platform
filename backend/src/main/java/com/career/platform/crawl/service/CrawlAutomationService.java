package com.career.platform.crawl.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.crawl.config.CrawlAutomationProperties;
import com.career.platform.crawl.entity.SystemConfig;
import com.career.platform.crawl.mapper.SystemConfigMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class CrawlAutomationService {

    private static final Logger log = LoggerFactory.getLogger(CrawlAutomationService.class);
    private static final DateTimeFormatter TASK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter COMMAND_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CONFIG_CHANNEL = "crawler";
    private static final String CONFIG_PREFIX = "crawl.automation.";
    private static final String CMD_WATCHDOG = "WATCHDOG";
    private static final String CMD_SYNC_AUTH = "SYNC_AUTH_SNAPSHOT";

    private final CrawlAutomationProperties defaults;
    private final SystemConfigMapper systemConfigMapper;
    private final CrawlSchedulerGateway crawlSchedulerGateway;
    private final ZhaopinAuthWatchdogService authWatchdogService;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CrawlAutomationService(CrawlAutomationProperties defaults,
                                  SystemConfigMapper systemConfigMapper,
                                  CrawlSchedulerGateway crawlSchedulerGateway,
                                  ZhaopinAuthWatchdogService authWatchdogService,
                                  ObjectMapper objectMapper) {
        this.defaults = defaults;
        this.systemConfigMapper = systemConfigMapper;
        this.crawlSchedulerGateway = crawlSchedulerGateway;
        this.authWatchdogService = authWatchdogService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> getAutomationStatus() {
        Map<String, Object> settings = loadSettings();
        Map<String, Object> agent = readAgentStatus();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("settings", settings);
        result.put("execution", buildExecutionStatus(settings, agent));
        result.put("scripts", authWatchdogService.describeScripts());
        result.put("agent", agent);
        result.put("pendingCommands", listPendingCommands());
        result.put("lastResults", listRecentResults(8));
        result.put("taskPreview", buildTaskPayload(settings));
        result.put("authStatus", fetchAuthStatusSafe());
        result.put("schedulerConfig", mapOf(
                "channel", settings.get("channel"),
                "cron", settings.get("cron"),
                "enabled", settings.get("enabled"),
                "watchdogEnabled", settings.get("watchdogEnabled")
        ));
        return result;
    }

    public Map<String, Object> updateAutomationConfig(Map<String, Object> request) {
        Map<String, Object> current = loadSettings();
        Map<String, Object> next = new LinkedHashMap<String, Object>(current);
        next.put("enabled", readBoolean(request.get("enabled"), readBoolean(current.get("enabled"), defaults.isEnabled())));
        next.put("cron", firstNonBlank(normalizeText(stringValue(request.get("cron"))), stringValue(current.get("cron"))));
        next.put("watchdogEnabled", readBoolean(request.get("watchdogEnabled"), readBoolean(current.get("watchdogEnabled"), defaults.isWatchdogEnabled())));
        next.put("pythonCommand", firstNonBlank(normalizeText(stringValue(request.get("pythonCommand"))), stringValue(current.get("pythonCommand"))));
        next.put("watchdogScript", firstNonBlank(normalizeText(stringValue(request.get("watchdogScript"))), stringValue(current.get("watchdogScript"))));
        next.put("syncScript", firstNonBlank(normalizeText(stringValue(request.get("syncScript"))), stringValue(current.get("syncScript"))));
        next.put("workspace", normalizeText(stringValue(request.get("workspace"))).trim());
        next.put("channel", firstNonBlank(normalizeText(stringValue(request.get("channel"))), stringValue(current.get("channel"))));
        next.put("taskNamePrefix", firstNonBlank(normalizeText(stringValue(request.get("taskNamePrefix"))), stringValue(current.get("taskNamePrefix"))));
        next.put("keywords", normalizeStringList(request.get("keywords"), listValue(current.get("keywords"))));
        next.put("cities", normalizeStringList(request.get("cities"), listValue(current.get("cities"))));
        next.put("pageCount", readInt(request.get("pageCount"), intValue(current.get("pageCount"), defaults.getPageCount())));
        next.put("priority", readInt(request.get("priority"), intValue(current.get("priority"), defaults.getPriority())));
        next.put("incremental", readBoolean(request.get("incremental"), readBoolean(current.get("incremental"), defaults.isIncremental())));
        next.put("incrementalPageLimit", readInt(request.get("incrementalPageLimit"), intValue(current.get("incrementalPageLimit"), defaults.getIncrementalPageLimit())));
        next.put("stalePageThreshold", readInt(request.get("stalePageThreshold"), intValue(current.get("stalePageThreshold"), defaults.getStalePageThreshold())));
        next.put("lookbackHours", readInt(request.get("lookbackHours"), intValue(current.get("lookbackHours"), defaults.getLookbackHours())));
        next.put("createUser", firstNonBlank(normalizeText(stringValue(request.get("createUser"))), stringValue(current.get("createUser"))));

        validateCron(stringValue(next.get("cron")));
        persistSettings(next);
        return getAutomationStatus();
    }

    public Map<String, Object> triggerConfiguredCollection(String triggerSource) {
        Map<String, Object> settings = loadSettings();
        if (!running.compareAndSet(false, true)) {
            return mapOf(
                    "triggerSource", triggerSource,
                    "status", "SKIPPED",
                    "message", "已有采集编排正在执行，本次触发已跳过。"
            );
        }

        LocalDateTime triggeredAt = LocalDateTime.now();
        persistMeta("lastTriggeredAt", triggeredAt.format(COMMAND_TIME_FORMAT));
        try {
            Map<String, Object> authDispatch = new LinkedHashMap<String, Object>();
            if (readBoolean(settings.get("watchdogEnabled"), false) && "zhaopin".equalsIgnoreCase(stringValue(settings.get("channel")))) {
                authDispatch = queueAutomationCommand(CMD_WATCHDOG, triggerSource);
            }

            Map<String, Object> taskPayload = buildTaskPayload(settings);
            Map<String, Object> schedulerResult = crawlSchedulerGateway.createTask(taskPayload);

            persistMeta("lastTriggerStatus", "CREATED");
            persistMeta("lastTriggerMessage", "采集任务已提交到调度中心。");

            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("triggerSource", triggerSource);
            response.put("status", "CREATED");
            response.put("triggeredAt", triggeredAt);
            response.put("taskPayload", taskPayload);
            response.put("authDispatch", authDispatch);
            response.put("scheduler", schedulerResult);
            return response;
        } catch (Exception e) {
            persistMeta("lastTriggerStatus", "FAILED");
            persistMeta("lastTriggerMessage", e.getMessage());
            throw e;
        } finally {
            running.set(false);
        }
    }

    public Map<String, Object> queueWatchdogCommand(String triggerSource) {
        return queueAutomationCommand(CMD_WATCHDOG, triggerSource);
    }

    public Map<String, Object> queueSyncAuthCommand(String triggerSource) {
        return queueAutomationCommand(CMD_SYNC_AUTH, triggerSource);
    }

    public void pollAndTriggerScheduledCollection() {
        Map<String, Object> settings = loadSettings();
        if (!readBoolean(settings.get("enabled"), false)) {
            return;
        }
        String cron = stringValue(settings.get("cron"));
        if (!StringUtils.hasText(cron)) {
            return;
        }

        CronExpression expression = validateCron(cron);
        LocalDateTime lastTriggeredAt = parseDateTime(stringValue(settings.get("lastTriggeredAt")));
        LocalDateTime reference = lastTriggeredAt != null ? lastTriggeredAt.minusSeconds(1) : LocalDateTime.now().minusMinutes(2);
        LocalDateTime next = expression.next(reference);
        if (next == null || next.isAfter(LocalDateTime.now())) {
            return;
        }

        try {
            triggerConfiguredCollection("SPRING_SCHEDULED");
        } catch (Exception e) {
            log.error("定时采集编排执行失败", e);
        }
    }

    public boolean isAutomationEnabled() {
        return readBoolean(loadSettings().get("enabled"), defaults.isEnabled());
    }

    public String getCron() {
        return stringValue(loadSettings().get("cron"));
    }

    private Map<String, Object> buildExecutionStatus(Map<String, Object> settings, Map<String, Object> agent) {
        Map<String, Object> docs = authWatchdogService.describeScripts();
        String configuredWorkspace = stringValue(settings.get("workspace")).trim();
        String agentWorkspace = stringValue(agent.get("repoRoot")).trim();
        String effectiveWorkspace = StringUtils.hasText(configuredWorkspace)
                ? configuredWorkspace
                : (StringUtils.hasText(agentWorkspace) ? agentWorkspace : "由宿主机代理自动识别仓库目录");

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.putAll(docs);
        result.put("workspace", configuredWorkspace);
        result.put("effectiveWorkspace", effectiveWorkspace);
        result.put("heartbeatFile", heartbeatFile().toString());
        result.put("commandDirectory", commandDirectory().toString());
        result.put("resultDirectory", resultDirectory().toString());
        result.put("agentOnline", agent.get("online"));
        result.put("agentStartCommand", firstNonBlank(stringValue(agent.get("startCommand")), "scripts\\start_crawl_automation_agent.cmd"));
        return result;
    }

    private Map<String, Object> queueAutomationCommand(String action, String triggerSource) {
        try {
            ensureCommandDirectories();
            for (Map<String, Object> pending : listPendingCommands()) {
                if (action.equals(stringValue(pending.get("action")))) {
                    return mapOf(
                            "status", "SKIPPED",
                            "message", "已有同类型自动化命令在队列中等待执行。",
                            "command", pending
                    );
                }
            }

            String commandId = "cmd_" + UUID.randomUUID().toString().replace("-", "");
            Map<String, Object> command = new LinkedHashMap<String, Object>();
            command.put("id", commandId);
            command.put("action", action);
            command.put("triggerSource", triggerSource);
            command.put("createdAt", LocalDateTime.now().format(COMMAND_TIME_FORMAT));
            command.put("workspace", stringValue(loadSettings().get("workspace")));
            command.put("pythonCommand", stringValue(loadSettings().get("pythonCommand")));
            command.put("watchdogScript", stringValue(loadSettings().get("watchdogScript")));
            command.put("syncScript", stringValue(loadSettings().get("syncScript")));
            writeJson(commandDirectory().resolve(commandId + ".json"), command);

            persistMeta(action.equals(CMD_WATCHDOG) ? "lastWatchdogCommandId" : "lastSyncCommandId", commandId);

            return mapOf(
                    "status", "QUEUED",
                    "message", "命令已写入自动化队列，等待宿主机代理执行。",
                    "command", command
            );
        } catch (IOException e) {
            throw BusinessException.of(500, "写入自动化命令失败: " + e.getMessage());
        }
    }

    private Map<String, Object> loadSettings() {
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put("enabled", defaults.isEnabled());
        settings.put("cron", defaults.getCron());
        settings.put("watchdogEnabled", defaults.isWatchdogEnabled());
        settings.put("workspace", defaults.getWorkspace());
        settings.put("pythonCommand", defaults.getPythonCommand());
        settings.put("watchdogScript", defaults.getWatchdogScript());
        settings.put("syncScript", defaults.getSyncScript());
        settings.put("channel", defaults.getChannel());
        settings.put("taskNamePrefix", defaults.getTaskNamePrefix());
        settings.put("keywords", new ArrayList<String>(defaults.getKeywords()));
        settings.put("cities", new ArrayList<String>(defaults.getCities()));
        settings.put("pageCount", defaults.getPageCount());
        settings.put("priority", defaults.getPriority());
        settings.put("incremental", defaults.isIncremental());
        settings.put("incrementalPageLimit", defaults.getIncrementalPageLimit());
        settings.put("stalePageThreshold", defaults.getStalePageThreshold());
        settings.put("lookbackHours", defaults.getLookbackHours());
        settings.put("createUser", defaults.getCreateUser());
        settings.put("lastTriggeredAt", "");
        settings.put("lastTriggerStatus", "");
        settings.put("lastTriggerMessage", "");
        settings.put("lastWatchdogCommandId", "");
        settings.put("lastSyncCommandId", "");

        for (SystemConfig config : listConfigs()) {
            String shortKey = config.getConfigKey().startsWith(CONFIG_PREFIX)
                    ? config.getConfigKey().substring(CONFIG_PREFIX.length())
                    : config.getConfigKey();
            Object value = decodeConfigValue(shortKey, config.getConfigValue());
            settings.put(shortKey, value);
        }
        return settings;
    }

    private void persistSettings(Map<String, Object> settings) {
        persistValue("enabled", String.valueOf(readBoolean(settings.get("enabled"), false)));
        persistValue("cron", stringValue(settings.get("cron")));
        persistValue("watchdogEnabled", String.valueOf(readBoolean(settings.get("watchdogEnabled"), true)));
        persistValue("workspace", stringValue(settings.get("workspace")));
        persistValue("pythonCommand", stringValue(settings.get("pythonCommand")));
        persistValue("watchdogScript", stringValue(settings.get("watchdogScript")));
        persistValue("syncScript", stringValue(settings.get("syncScript")));
        persistValue("channel", stringValue(settings.get("channel")));
        persistValue("taskNamePrefix", stringValue(settings.get("taskNamePrefix")));
        persistValue("keywords", writeList(listValue(settings.get("keywords"))));
        persistValue("cities", writeList(listValue(settings.get("cities"))));
        persistValue("pageCount", String.valueOf(intValue(settings.get("pageCount"), defaults.getPageCount())));
        persistValue("priority", String.valueOf(intValue(settings.get("priority"), defaults.getPriority())));
        persistValue("incremental", String.valueOf(readBoolean(settings.get("incremental"), defaults.isIncremental())));
        persistValue("incrementalPageLimit", String.valueOf(intValue(settings.get("incrementalPageLimit"), defaults.getIncrementalPageLimit())));
        persistValue("stalePageThreshold", String.valueOf(intValue(settings.get("stalePageThreshold"), defaults.getStalePageThreshold())));
        persistValue("lookbackHours", String.valueOf(intValue(settings.get("lookbackHours"), defaults.getLookbackHours())));
        persistValue("createUser", stringValue(settings.get("createUser")));
    }

    private Map<String, Object> buildTaskPayload(Map<String, Object> settings) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("task_name", stringValue(settings.get("taskNamePrefix")) + "-" + TASK_TIME_FORMAT.format(LocalDateTime.now()));
        payload.put("channel", stringValue(settings.get("channel")));
        putListIfPresent(payload, "keywords", listValue(settings.get("keywords")));
        putListIfPresent(payload, "city", listValue(settings.get("cities")));
        payload.put("page_count", intValue(settings.get("pageCount"), defaults.getPageCount()));
        payload.put("priority", intValue(settings.get("priority"), defaults.getPriority()));
        payload.put("schedule_type", "IMMEDIATE");
        payload.put("incremental", readBoolean(settings.get("incremental"), defaults.isIncremental()));
        payload.put("incremental_page_limit", intValue(settings.get("incrementalPageLimit"), defaults.getIncrementalPageLimit()));
        payload.put("stale_page_threshold", intValue(settings.get("stalePageThreshold"), defaults.getStalePageThreshold()));
        payload.put("lookback_hours", intValue(settings.get("lookbackHours"), defaults.getLookbackHours()));
        payload.put("create_user", stringValue(settings.get("createUser")));
        return payload;
    }

    private void putListIfPresent(Map<String, Object> payload, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            payload.put(key, values);
        }
    }

    private List<SystemConfig> listConfigs() {
        return systemConfigMapper.selectList(new QueryWrapper<SystemConfig>()
                .eq("channel", CONFIG_CHANNEL)
                .likeRight("config_key", CONFIG_PREFIX)
                .eq("status", 1));
    }

    private void persistMeta(String key, String value) {
        persistValue(key, value == null ? "" : value);
    }

    private void persistValue(String shortKey, String value) {
        String configKey = CONFIG_PREFIX + shortKey;
        SystemConfig existing = systemConfigMapper.selectOne(new QueryWrapper<SystemConfig>()
                .eq("channel", CONFIG_CHANNEL)
                .eq("config_key", configKey)
                .last("LIMIT 1"));
        if (existing == null) {
            existing = new SystemConfig();
            existing.setChannel(CONFIG_CHANNEL);
            existing.setConfigKey(configKey);
            existing.setStatus(1);
            existing.setConfigValue(value == null ? "" : value);
            systemConfigMapper.insert(existing);
            return;
        }
        existing.setConfigValue(value == null ? "" : value);
        existing.setStatus(1);
        systemConfigMapper.updateById(existing);
    }

    private Object decodeConfigValue(String shortKey, String rawValue) {
        if ("enabled".equals(shortKey) || "watchdogEnabled".equals(shortKey) || "incremental".equals(shortKey)) {
            return Boolean.parseBoolean(rawValue);
        }
        if ("pageCount".equals(shortKey) || "priority".equals(shortKey) || "incrementalPageLimit".equals(shortKey)
                || "stalePageThreshold".equals(shortKey) || "lookbackHours".equals(shortKey)) {
            return intValue(rawValue, 0);
        }
        if ("keywords".equals(shortKey) || "cities".equals(shortKey)) {
            return readList(rawValue);
        }
        return rawValue;
    }

    private List<String> readList(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return new ArrayList<String>();
        }
        try {
            return objectMapper.readValue(rawValue, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return normalizeStringList(rawValue, new ArrayList<String>());
        }
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? new ArrayList<String>() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Map<String, Object> fetchAuthStatusSafe() {
        try {
            Map<String, Object> response = crawlSchedulerGateway.fetchZhaopinAuthStatus();
            Object data = response.get("data");
            return data instanceof Map ? (Map<String, Object>) data : new LinkedHashMap<String, Object>();
        } catch (Exception e) {
            return mapOf("status", "UNAVAILABLE", "message", e.getMessage());
        }
    }

    private Map<String, Object> readAgentStatus() {
        Map<String, Object> heartbeat = readJsonFile(heartbeatFile());
        LocalDateTime updatedAt = parseDateTime(stringValue(heartbeat.get("updatedAt")));
        boolean online = updatedAt != null && updatedAt.isAfter(LocalDateTime.now().minusMinutes(2));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("online", online);
        result.put("updatedAt", updatedAt == null ? stringValue(heartbeat.get("updatedAt")) : updatedAt);
        result.put("hostname", heartbeat.get("hostname"));
        result.put("pid", heartbeat.get("pid"));
        result.put("repoRoot", heartbeat.get("repoRoot"));
        result.put("automationHome", heartbeat.get("automationHome"));
        result.put("commandDir", heartbeat.get("commandDir"));
        result.put("resultDir", heartbeat.get("resultDir"));
        result.put("pythonExecutable", heartbeat.get("pythonExecutable"));
        result.put("currentCommandId", heartbeat.get("currentCommandId"));
        result.put("currentAction", heartbeat.get("currentAction"));
        result.put("lastCommandId", heartbeat.get("lastCommandId"));
        result.put("lastCommandStatus", heartbeat.get("lastCommandStatus"));
        result.put("pendingCount", listPendingCommands().size());
        result.put("startCommand", "scripts\\start_crawl_automation_agent.cmd");
        return result;
    }

    private List<Map<String, Object>> listPendingCommands() {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try {
            ensureCommandDirectories();
            Files.list(commandDirectory())
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> result.add(readJsonFile(path)));
        } catch (IOException ignored) {
            return result;
        }
        return result;
    }

    private List<Map<String, Object>> listRecentResults(int limit) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        try {
            ensureCommandDirectories();
            Files.list(resultDirectory())
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readJsonFile)
                    .sorted(new Comparator<Map<String, Object>>() {
                        @Override
                        public int compare(Map<String, Object> a, Map<String, Object> b) {
                            return stringValue(b.get("finishedAt")).compareTo(stringValue(a.get("finishedAt")));
                        }
                    })
                    .limit(limit)
                    .forEach(result::add);
        } catch (IOException ignored) {
            return result;
        }
        return result;
    }

    private void ensureCommandDirectories() throws IOException {
        Files.createDirectories(commandDirectory());
        Files.createDirectories(resultDirectory());
    }

    private Path automationHome() {
        String raw = System.getenv("CRAWL_AUTOMATION_HOME");
        if (!StringUtils.hasText(raw)) {
            raw = "tmp/automation";
        }
        return Paths.get(raw);
    }

    private Path commandDirectory() {
        return automationHome().resolve("commands");
    }

    private Path resultDirectory() {
        return automationHome().resolve("results");
    }

    private Path heartbeatFile() {
        return automationHome().resolve("agent-heartbeat.json");
    }

    private void writeJson(Path path, Map<String, Object> payload) throws IOException {
        Files.write(path, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> readJsonFile(Path path) {
        try {
            if (!Files.exists(path)) {
                return new LinkedHashMap<String, Object>();
            }
            return objectMapper.readValue(Files.readAllBytes(path), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return mapOf("file", path.getFileName().toString(), "error", e.getMessage());
        }
    }

    private CronExpression validateCron(String cron) {
        if (!StringUtils.hasText(cron)) {
            throw BusinessException.of(400, "Cron 表达式不能为空。");
        }
        try {
            return CronExpression.parse(cron.trim());
        } catch (Exception e) {
            throw BusinessException.of(400, "Cron 表达式无效: " + cron);
        }
    }

    private LocalDateTime parseDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), COMMAND_TIME_FORMAT);
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(raw.trim());
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private List<String> normalizeStringList(Object value, List<String> fallback) {
        List<String> result = new ArrayList<String>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(normalizeText(String.valueOf(item)).trim());
                }
            }
            return result;
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (!StringUtils.hasText(raw)) {
                return result;
            }
            String[] parts = raw.split("[,，、\\s]+");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    result.add(normalizeText(part).trim());
                }
            }
            return result;
        }
        return fallback == null ? result : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<String> listValue(Object value) {
        if (value instanceof List) {
            return (List<String>) value;
        }
        return new ArrayList<String>();
    }

    private int intValue(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int readInt(Object value, int fallback) {
        int parsed = intValue(value, fallback);
        return parsed <= 0 ? fallback : parsed;
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return value == null ? "" : value;
        }
        String trimmed = value.trim();
        String repaired = repairIso88591(trimmed);
        return containsMoreReadableCjk(repaired, trimmed) ? repaired : trimmed;
    }

    private String repairIso88591(String value) {
        try {
            return new String(value.getBytes("ISO-8859-1"), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return value;
        }
    }

    private boolean containsMoreReadableCjk(String candidate, String original) {
        return countCjk(candidate) > countCjk(original);
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

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : second;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            result.put(String.valueOf(values[i]), values[i + 1]);
        }
        return result;
    }
}
