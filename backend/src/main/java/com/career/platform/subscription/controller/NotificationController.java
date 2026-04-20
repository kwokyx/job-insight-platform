package com.career.platform.subscription.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.career.platform.common.annotation.Log;
import com.career.platform.common.exception.BusinessException;
import com.career.platform.common.result.R;
import com.career.platform.subscription.entity.Notification;
import com.career.platform.subscription.mapper.NotificationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知中心
 */
@Tag(name = "通知中心", description = "站内通知查看、标记已读")
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationMapper notificationMapper;

    public NotificationController(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    @Operation(summary = "通知列表（分页）")
    @GetMapping
    public R<?> listNotifications(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        Long userId = getCurrentUserId();
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (type != null) wrapper.eq(Notification::getNotifyType, type);
        if (isRead != null) wrapper.eq(Notification::getIsRead, isRead);
        wrapper.orderByDesc(Notification::getCreatedAt);

        IPage<Notification> result = notificationMapper.selectPage(new Page<>(page, pageSize), wrapper);

        // 未读数
        Long unreadCount = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
        );

        Map<String, Object> extra = new HashMap<>();
        extra.put("records", result.getRecords());
        extra.put("total", result.getTotal());
        extra.put("unreadCount", unreadCount);
        return R.ok(extra);
    }

    @Log("标记通知已读")
    @Operation(summary = "标记通知已读")
    @PutMapping("/{id}/read")
    public R<?> markRead(@PathVariable Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(getCurrentUserId())) {
            throw BusinessException.notFound("通知不存在");
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
        return R.ok("已标记为已读");
    }

    @Log("全部标记已读")
    @Operation(summary = "全部标记已读")
    @PutMapping("/read-all")
    public R<?> markAllRead() {
        Long userId = getCurrentUserId();
        Notification update = new Notification();
        update.setIsRead(1);
        notificationMapper.update(update,
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0)
        );
        return R.ok("全部已读");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || "anonymousUser".equals(auth.getPrincipal())) {
            throw BusinessException.unauthorized("请先登录");
        }
        return (Long) auth.getPrincipal();
    }
}
