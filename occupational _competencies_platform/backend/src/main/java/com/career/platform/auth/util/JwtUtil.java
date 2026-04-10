package com.career.platform.auth.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 (jjwt 0.11.x API)
 */
@Component
public class JwtUtil {

    @Value("${career.jwt.secret}")
    private String secret;

    @Value("${career.jwt.access-token-expire}")
    private long accessTokenExpire;

    @Value("${career.jwt.refresh-token-expire}")
    private long refreshTokenExpire;

    private SecretKey getKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 AccessToken
     */
    public String generateAccessToken(Long userId, String username, Integer roleType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpire * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("roleType", roleType);
        claims.put("tokenType", "access");

        return Jwts.builder()
                .setSubject(userId.toString())
                .addClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成 RefreshToken
     */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpire * 1000);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenType", "refresh");

        return Jwts.builder()
                .setSubject(userId.toString())
                .addClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 提取用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 提取角色类型
     */
    public Integer getRoleType(String token) {
        return parseToken(token).get("roleType", Integer.class);
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * AccessToken 过期时间(秒)
     */
    public long getAccessTokenExpire() {
        return accessTokenExpire;
    }
}
