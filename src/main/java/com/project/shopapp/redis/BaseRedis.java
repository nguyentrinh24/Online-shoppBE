package com.project.shopapp.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;

@Component
public class BaseRedis {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

        public BaseRedis(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
            this.redisTemplate = redisTemplate;
            this.objectMapper = objectMapper;
        }

        public void setProduct(String key, Object value) {

            redisTemplate.opsForValue().set(key, value);
        }

        public void setProductWithExpiration(String key, Object value, long timeout, TimeUnit unit) {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        }

        public Object getProduct(String key) {
            return redisTemplate.opsForValue().get(key);
        }

        public void deleteProduct(String key) {

            redisTemplate.delete(key);
        }


} 