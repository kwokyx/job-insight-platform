package com.career.platform.crawl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "career.crawl.automation")
public class CrawlAutomationProperties {

    private boolean enabled = false;
    private String cron = "0 0 7,13,19 * * ?";
    private boolean watchdogEnabled = true;
    private String workspace = "";
    private String pythonCommand = "py -3";
    private String watchdogScript = "scripts/watch_zhaopin_auth.py";
    private String syncScript = "scripts/sync_zhaopin_auth_snapshot.py";
    private String channel = "zhaopin";
    private String taskNamePrefix = "智联定时采集";
    private List<String> keywords = new ArrayList<String>() {{
        add("Python");
    }};
    private List<String> cities = new ArrayList<String>() {{
        add("成都");
    }};
    private int pageCount = 3;
    private int priority = 5;
    private boolean incremental = true;
    private int incrementalPageLimit = 2;
    private int stalePageThreshold = 1;
    private int lookbackHours = 72;
    private String createUser = "backend-scheduler";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public boolean isWatchdogEnabled() {
        return watchdogEnabled;
    }

    public void setWatchdogEnabled(boolean watchdogEnabled) {
        this.watchdogEnabled = watchdogEnabled;
    }

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getPythonCommand() {
        return pythonCommand;
    }

    public void setPythonCommand(String pythonCommand) {
        this.pythonCommand = pythonCommand;
    }

    public String getWatchdogScript() {
        return watchdogScript;
    }

    public void setWatchdogScript(String watchdogScript) {
        this.watchdogScript = watchdogScript;
    }

    public String getSyncScript() {
        return syncScript;
    }

    public void setSyncScript(String syncScript) {
        this.syncScript = syncScript;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getTaskNamePrefix() {
        return taskNamePrefix;
    }

    public void setTaskNamePrefix(String taskNamePrefix) {
        this.taskNamePrefix = taskNamePrefix;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }

    public List<String> getCities() {
        return cities;
    }

    public void setCities(List<String> cities) {
        this.cities = cities;
    }

    public int getPageCount() {
        return pageCount;
    }

    public void setPageCount(int pageCount) {
        this.pageCount = pageCount;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public void setIncremental(boolean incremental) {
        this.incremental = incremental;
    }

    public int getIncrementalPageLimit() {
        return incrementalPageLimit;
    }

    public void setIncrementalPageLimit(int incrementalPageLimit) {
        this.incrementalPageLimit = incrementalPageLimit;
    }

    public int getStalePageThreshold() {
        return stalePageThreshold;
    }

    public void setStalePageThreshold(int stalePageThreshold) {
        this.stalePageThreshold = stalePageThreshold;
    }

    public int getLookbackHours() {
        return lookbackHours;
    }

    public void setLookbackHours(int lookbackHours) {
        this.lookbackHours = lookbackHours;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }
}
