package com.career.platform.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;           // user / assistant / system
    private String content;
    private String contentType;    // text / chart / table
    private String metadata;       // JSON
    private Integer tokensUsed;
    private Integer latencyMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
