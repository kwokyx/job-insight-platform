package com.career.platform.crawl.service;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.crawl.config.CrawlAutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZhaopinAuthWatchdogService {

    private static final Logger log = LoggerFactory.getLogger(ZhaopinAuthWatchdogService.class);

    private final CrawlAutomationProperties properties;

    public ZhaopinAuthWatchdogService(CrawlAutomationProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> ensureAuthReady() {
        return runScript(properties.getWatchdogScript(), "智联鉴权看门狗");
    }

    public Map<String, Object> syncAuthSnapshot() {
        return runScript(properties.getSyncScript(), "智联鉴权快照同步");
    }

    public Map<String, Object> describeScripts() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", "HOST_AGENT_QUEUE");
        result.put("modeLabel", "宿主机代理执行");
        result.put("workspace", properties.getWorkspace());
        result.put("workspaceConfigured", StringUtils.hasText(properties.getWorkspace()));
        result.put("pythonCommand", properties.getPythonCommand());
        result.put("agentStartCommand", "scripts\\start_crawl_automation_agent.cmd");
        result.put("watchdogScript", scriptMeta(properties.getWatchdogScript()));
        result.put("syncScript", scriptMeta(properties.getSyncScript()));
        result.put("notes", java.util.Arrays.asList(
                "后端只负责把命令写入共享队列，不直接在容器内启动浏览器或执行本机脚本。",
                "实际脚本由宿主机代理监听 tmp/automation/commands 后执行。",
                "如果代理离线，平台仍可创建采集任务，但鉴权维护脚本不会自动运行。"
        ));
        return result;
    }

    private Map<String, Object> runScript(String scriptPath, String scriptLabel) {
        List<String> command = buildPythonCommand(scriptPath);
        File workspace = new File(properties.getWorkspace());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace);
        builder.redirectErrorStream(true);

        String output;
        int exitCode;
        try {
            Process process = builder.start();
            StringBuilder buffer = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                buffer.append(line).append(System.lineSeparator());
            }
            exitCode = process.waitFor();
            output = buffer.toString().trim();
        } catch (Exception e) {
            throw BusinessException.of(500, scriptLabel + "执行失败: " + e.getMessage());
        }

        Map<String, Object> result = summarize(scriptPath, output, exitCode);
        if (exitCode != 0) {
            throw BusinessException.of(500, scriptLabel + "执行失败");
        }
        log.info("{} executed successfully, script={}, exitCode={}", scriptLabel, scriptPath, exitCode);
        return result;
    }

    private List<String> buildPythonCommand(String scriptPath) {
        List<String> command = new ArrayList<String>();
        String[] pythonParts = properties.getPythonCommand().trim().split("\\s+");
        for (String part : pythonParts) {
            if (StringUtils.hasText(part)) {
                command.add(part.trim());
            }
        }
        command.add(scriptPath);
        return command;
    }

    private Map<String, Object> summarize(String scriptPath, String output, int exitCode) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("script", scriptPath);
        result.put("executedAt", LocalDateTime.now());
        result.put("exitCode", exitCode);
        result.put("success", exitCode == 0);
        result.put("rawOutput", output == null ? "" : output);
        return result;
    }

    private Map<String, Object> scriptMeta(String relativePath) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("relativePath", relativePath);
        result.put("backendVisible", false);
        result.put("message", "由宿主机代理按相对路径执行，不以容器内文件存在性作为可用性判断。");
        return result;
    }
}
