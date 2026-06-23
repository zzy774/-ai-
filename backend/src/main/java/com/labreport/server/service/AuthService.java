package com.labreport.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labreport.server.common.BusinessException;
import com.labreport.server.common.Constants;
import com.labreport.server.model.dto.LoginRequest;
import com.labreport.server.model.dto.LoginResponse;
import com.labreport.server.model.entity.User;
import com.labreport.server.model.mapper.UserMapper;
import com.labreport.server.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    public LoginResponse login(LoginRequest req) {
        // 检查是否被锁定
        String failKey = Constants.LOGIN_FAIL_KEY + req.getUsername();
        String failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= Constants.MAX_LOGIN_FAIL_COUNT) {
            throw new BusinessException(429, "账户已被临时锁定，请" + Constants.LOGIN_LOCK_MINUTES + "分钟后再试");
        }

        // 查用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, req.getUsername()));
        if (user == null || user.getStatus() == 0) {
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            // 记录失败次数
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                redisTemplate.expire(failKey, Duration.ofMinutes(Constants.LOGIN_LOCK_MINUTES));
            }
            throw new BusinessException(400, "用户名或密码错误");
        }

        // 登录成功，清除失败计数
        redisTemplate.delete(failKey);

        // 更新最后登录时间
        user.setLoginFailCount(0);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 生成JWT
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();

        log.info("用户登录成功: {}", user.getUsername());
        return new LoginResponse(token, Constants.JWT_EXPIRATION_MS,
            user.getUsername(), displayName);
    }

    public void logout(String token) {
        // Token加入黑名单
        String blacklistKey = Constants.TOKEN_BLACKLIST_KEY + token;
        redisTemplate.opsForValue().set(blacklistKey, "1",
            Duration.ofMillis(jwtTokenProvider.getExpirationMs()));
        log.info("Token已加入黑名单");
    }

    public User getCurrentUser(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username));
    }
}
