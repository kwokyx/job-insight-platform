package com.career.platform.subscription.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.subscription.entity.UserSubscription;
import com.career.platform.subscription.mapper.UserSubscriptionMapper;
import com.career.platform.subscription.service.PushService;
import com.career.platform.subscription.service.SubscriptionDeliveryProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Subscription", description = "Student-side job push subscriptions")
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private final UserSubscriptionMapper subscriptionMapper;
    private final PushService pushService;
    private final SubscriptionDeliveryProperties subscriptionDeliveryProperties;

    public SubscriptionController(UserSubscriptionMapper subscriptionMapper,
                                  PushService pushService,
                                  SubscriptionDeliveryProperties subscriptionDeliveryProperties) {
        this.subscriptionMapper = subscriptionMapper;
        this.pushService = pushService;
        this.subscriptionDeliveryProperties = subscriptionDeliveryProperties;
    }

    public static class CreateSubscriptionRequest {
        private String subscriptionType = "JOB_PUSH";
        private String filterConfig;
        private String channel = "IN_APP";

        public String getSubscriptionType() { return subscriptionType; }
        public void setSubscriptionType(String subscriptionType) { this.subscriptionType = subscriptionType; }
        public String getFilterConfig() { return filterConfig; }
        public void setFilterConfig(String filterConfig) { this.filterConfig = filterConfig; }
        public String getChannel() { return channel; }
        public void setChannel(String channel) { this.channel = channel; }
    }

    @Log("Create subscription")
    @Operation(summary = "Create subscription")
    @PostMapping
    public R<?> create(@Valid @RequestBody CreateSubscriptionRequest req) {
        String channel = StringUtils.hasText(req.getChannel()) ? req.getChannel().trim().toUpperCase() : "IN_APP";
        if (!"IN_APP".equals(channel) && !"EMAIL".equals(channel)) {
            throw BusinessException.of(400, "当前仅支持站内通知和邮件通知");
        }
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(getCurrentUserId());
        subscription.setSubscriptionType(StringUtils.hasText(req.getSubscriptionType()) ? req.getSubscriptionType() : "JOB_PUSH");
        subscription.setFilterConfig(req.getFilterConfig());
        subscription.setFilterCriteria(req.getFilterConfig());
        subscription.setChannel(channel);
        subscription.setPushChannel(channel);
        subscription.setIsActive(1);
        subscription.setCreatedAt(LocalDateTime.now());
        subscriptionMapper.insert(subscription);
        return R.ok("Subscription created", subscription);
    }

    @Operation(summary = "Subscription delivery meta")
    @GetMapping("/meta")
    public R<?> meta() {
        return R.ok(subscriptionDeliveryProperties.buildMeta());
    }

    @Operation(summary = "Subscription list")
    @GetMapping
    public R<?> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        IPage<UserSubscription> result = subscriptionMapper.selectPage(
                new Page<>(page, pageSize),
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getUserId, getCurrentUserId())
                        .orderByDesc(UserSubscription::getCreatedAt)
        );
        return R.page(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Log("Delete subscription")
    @Operation(summary = "Delete subscription")
    @DeleteMapping("/{id}")
    public R<?> delete(@PathVariable Long id) {
        UserSubscription subscription = subscriptionMapper.selectById(id);
        if (subscription == null || !subscription.getUserId().equals(getCurrentUserId())) {
            throw BusinessException.notFound("Subscription not found");
        }
        subscriptionMapper.deleteById(id);
        return R.ok("Subscription deleted");
    }

    @Operation(summary = "Matched jobs for subscription")
    @GetMapping("/{id}/matches")
    public R<?> matches(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return R.ok(pushService.findMatches(id, getCurrentUserId(), limit));
    }

    @Log("Dispatch subscription matches")
    @Operation(summary = "Dispatch subscription matches to notifications and email")
    @PostMapping("/{id}/dispatch")
    public R<?> dispatch(
            @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit
    ) {
        int count = pushService.dispatchMatchesForSubscription(id, getCurrentUserId(), limit);
        Map<String, Object> data = new HashMap<>();
        data.put("deliveredCount", count);
        return R.ok("Subscription matches dispatched", data);
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("登录状态已失效，请重新登录");
        }
        return (Long) auth.getPrincipal();
    }
}
