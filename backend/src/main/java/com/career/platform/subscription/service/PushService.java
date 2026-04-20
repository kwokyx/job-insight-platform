package com.career.platform.subscription.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.job.entity.JobPosting;
import com.career.platform.job.mapper.JobPostingMapper;
import com.career.platform.subscription.entity.Notification;
import com.career.platform.subscription.entity.UserSubscription;
import com.career.platform.subscription.mapper.NotificationMapper;
import com.career.platform.subscription.mapper.UserSubscriptionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final UserSubscriptionMapper subscriptionMapper;
    private final JobPostingMapper jobPostingMapper;
    private final NotificationMapper notificationMapper;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;

    public PushService(UserSubscriptionMapper subscriptionMapper, JobPostingMapper jobPostingMapper,
                       NotificationMapper notificationMapper, WebhookService webhookService,
                       ObjectMapper objectMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.jobPostingMapper = jobPostingMapper;
        this.notificationMapper = notificationMapper;
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    public List<JobPosting> findMatches(Long subscriptionId, Long userId, int limit) {
        UserSubscription subscription = subscriptionMapper.selectById(subscriptionId);
        if (subscription == null || !subscription.getUserId().equals(userId)) {
            throw BusinessException.notFound("Subscription not found");
        }
        return findMatches(subscription, limit);
    }

    public List<JobPosting> findMatches(UserSubscription subscription, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        int baseLimit = Math.max(20, Math.min(safeLimit * 10, 200));

        LambdaQueryWrapper<JobPosting> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(JobPosting::getPublishDate)
                .last("LIMIT " + baseLimit);

        JsonNode filterNode = parseFilterConfig(subscription.getFilterConfig());
        if (filterNode != null) {
            likeIfPresent(wrapper, JobPosting::getCity, filterNode, "city");
            likeIfPresent(wrapper, JobPosting::getIndustryName, filterNode, "industry");
            likeIfPresent(wrapper, JobPosting::getTitle, filterNode, "keyword");
            likeIfPresent(wrapper, JobPosting::getEducation, filterNode, "education");
        }

        List<JobPosting> baseCandidates = jobPostingMapper.selectList(wrapper);
        List<JobPosting> filtered = new ArrayList<>();
        for (JobPosting job : baseCandidates) {
            if (!matches(job, filterNode)) {
                continue;
            }
            filtered.add(job);
            if (filtered.size() >= safeLimit) {
                break;
            }
        }
        return filtered;
    }

    public int dispatchMatchesForSubscription(Long subscriptionId, Long userId, int limit) {
        UserSubscription subscription = subscriptionMapper.selectById(subscriptionId);
        if (subscription == null || !subscription.getUserId().equals(userId)) {
            throw BusinessException.notFound("Subscription not found");
        }
        List<JobPosting> matches = findMatches(subscription, limit);
        createNotifications(subscription, matches);
        webhookService.deliverJobMatches(subscription.getUserId(), matches);
        subscription.setLastPushedAt(LocalDateTime.now());
        subscriptionMapper.updateById(subscription);
        return matches.size();
    }

    public int dispatchAllActiveSubscriptions(int limitPerSubscription) {
        List<UserSubscription> subscriptions = subscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getIsActive, 1)
        );
        int total = 0;
        for (UserSubscription subscription : subscriptions) {
            List<JobPosting> matches = findMatches(subscription, limitPerSubscription);
            createNotifications(subscription, matches);
            webhookService.deliverJobMatches(subscription.getUserId(), matches);
            subscription.setLastPushedAt(LocalDateTime.now());
            subscriptionMapper.updateById(subscription);
            total += matches.size();
        }
        log.info("Dispatched {} matches across {} subscriptions", total, subscriptions.size());
        return total;
    }

    private boolean matches(JobPosting job, JsonNode filterNode) {
        if (filterNode == null) {
            return true;
        }
        if (!matchesSalary(job, filterNode)) {
            return false;
        }
        if (!matchesExperience(job, filterNode)) {
            return false;
        }
        return matchesSkills(job, filterNode);
    }

    private boolean matchesSalary(JobPosting job, JsonNode filterNode) {
        BigDecimal jobMin = job.getSalaryMin() == null ? BigDecimal.ZERO : job.getSalaryMin();
        BigDecimal jobMax = job.getSalaryMax() == null ? jobMin : job.getSalaryMax();
        JsonNode minNode = filterNode.get("salaryMin");
        JsonNode maxNode = filterNode.get("salaryMax");
        if (minNode != null && minNode.isNumber() && jobMax.compareTo(BigDecimal.valueOf(minNode.asDouble())) < 0) {
            return false;
        }
        if (maxNode != null && maxNode.isNumber() && jobMin.compareTo(BigDecimal.valueOf(maxNode.asDouble())) > 0) {
            return false;
        }
        return true;
    }

    private boolean matchesExperience(JobPosting job, JsonNode filterNode) {
        JsonNode experienceNode = filterNode.get("experience");
        if (experienceNode == null || !StringUtils.hasText(experienceNode.asText())) {
            return true;
        }
        return job.getExperience() != null
                && job.getExperience().toLowerCase().contains(experienceNode.asText().trim().toLowerCase());
    }

    private boolean matchesSkills(JobPosting job, JsonNode filterNode) {
        JsonNode skillsNode = filterNode.get("skills");
        if (skillsNode == null || !skillsNode.isArray() || skillsNode.size() == 0) {
            return true;
        }

        Set<String> expectedSkills = new LinkedHashSet<>();
        for (JsonNode item : skillsNode) {
            if (item != null && StringUtils.hasText(item.asText())) {
                expectedSkills.add(item.asText().trim().toLowerCase());
            }
        }
        if (expectedSkills.isEmpty()) {
            return true;
        }

        Set<String> jobSkills = new LinkedHashSet<>();
        for (String skill : jobPostingMapper.jobSkills(job.getId())) {
            if (StringUtils.hasText(skill)) {
                jobSkills.add(skill.trim().toLowerCase());
            }
        }
        for (String skill : expectedSkills) {
            if (jobSkills.contains(skill)) {
                return true;
            }
        }
        return false;
    }

    private void createNotifications(UserSubscription subscription, List<JobPosting> matches) {
        for (JobPosting job : matches) {
            Notification notification = new Notification();
            notification.setUserId(subscription.getUserId());
            notification.setTitle("岗位订阅命中");
            notification.setContent("岗位《" + job.getTitle() + "》符合你的订阅条件。");
            notification.setNotifyType("JOB_PUSH");
            notification.setRefId(job.getId());
            notification.setIsRead(0);
            notification.setCreatedAt(LocalDateTime.now());
            notificationMapper.insert(notification);
        }
    }

    private JsonNode parseFilterConfig(String filterConfig) {
        if (!StringUtils.hasText(filterConfig)) {
            return null;
        }
        try {
            return objectMapper.readTree(filterConfig);
        } catch (Exception e) {
            log.warn("Invalid subscription filter config: {}", filterConfig);
            return null;
        }
    }

    private void likeIfPresent(LambdaQueryWrapper<JobPosting> wrapper,
                               com.baomidou.mybatisplus.core.toolkit.support.SFunction<JobPosting, String> column,
                               JsonNode node,
                               String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value != null && StringUtils.hasText(value.asText())) {
            wrapper.like(column, value.asText().trim());
        }
    }
}
