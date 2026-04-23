package com.career.platform.crawl.service;

import com.career.platform.common.exception.BusinessException;
import com.career.platform.crawl.config.CrawlAutomationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
        List<String> command = new ArrayList<String>();
        String[] pythonParts = properties.getPythonCommand().trim().split("\\s+");
        for (String part : pythonParts) {
            if (!part.trim().isEmpty()) {
                command.add(part.trim());
            }
        }
        command.add(properties.getWatchdogScript());

        File workspace = new File(properties.getWorkspace());
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workspace);
        builder.redirectErrorStream(true);

        String output = "";
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
            throw BusinessException.of(500, "执行智联鉴权看门狗失败: " + e.getMessage());
        }

        Map<String, Object> result = summarize(output, exitCode);
        if (exitCode != 0) {
            String failingStep = String.valueOf(result.get("failingStep"));
            throw BusinessException.of(500, "智联鉴权看门狗失败，失败步骤: " + failingStep);
        }

        log.info("智联鉴权看门狗执行完成: refreshed={}, syncOnly={}, nodesOnline={}",
                result.get("authRefreshed"), result.get("syncOnly"), result.get("threeVmNodesOnline"));
        return result;
    }

    private Map<String, Object> summarize(String output, int exitCode) {
        String safeOutput = output == null ? "" : output;
        boolean fallbackSync = safeOutput.contains("fallback to sync current snapshot");
        boolean syncSucceeded = safeOutput.contains("auth snapshot synchronized");
        boolean refreshed = safeOutput.contains("trying local Edge refresh") && !fallbackSync;
        boolean threeVmNodesOnline = containsAll(safeOutput, "=== hadoop001", "=== hadoop002", "=== hadoop003");
        String failingStep = "";
        if (exitCode != 0) {
            if (safeOutput.contains("fallback to sync current snapshot")) {
                failingStep = "本地 Edge 刷新鉴权";
            } else if (safeOutput.contains("auth snapshot synchronized")) {
                failingStep = "鉴权同步后的状态校验";
            } else {
                failingStep = "智联鉴权看门狗执行";
            }
        } else if (fallbackSync) {
            failingStep = "本地 Edge 刷新鉴权";
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("executedAt", LocalDateTime.now());
        result.put("authRefreshed", refreshed);
        result.put("syncOnly", syncSucceeded && !refreshed);
        result.put("threeVmNodesOnline", threeVmNodesOnline);
        result.put("failingStep", failingStep);
        result.put("rawOutput", safeOutput);
        return result;
    }

    private boolean containsAll(String text, String first, String second, String third) {
        return text.contains(first) && text.contains(second) && text.contains(third);
    }
}
