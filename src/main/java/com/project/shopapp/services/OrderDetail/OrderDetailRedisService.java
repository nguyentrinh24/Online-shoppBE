package com.project.shopapp.services.OrderDetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.shopapp.models.OrderDetail;
import com.project.shopapp.redis.BaseRedis;
import com.project.shopapp.responses.OrderDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OrderDetailRedisService {
    private final BaseRedis baseRedis;
    private final ObjectMapper redisObjectMapper;
    
    @Value("${spring.data.redis.use-redis-cache}")
    private boolean useRedisCache;
    
    private static final String ORDER_DETAIL_KEY_PREFIX = "order_detail:";
    private static final String ORDER_DETAILS_KEY_PREFIX = "order_details:";
    private static final long ORDER_DETAIL_CACHE_DURATION = 24; // 24 hours

    public OrderDetail getOrderDetailFromCache(Long orderDetailId) throws JsonProcessingException {
        if (!useRedisCache) {
            return null;
        }
        String key = ORDER_DETAIL_KEY_PREFIX + orderDetailId;
        String json = (String) baseRedis.getProduct(key);
        if (json != null) {
            return redisObjectMapper.readValue(json, OrderDetail.class);
        }
        return null;
    }

    public void saveOrderDetailToCache(OrderDetail orderDetail) throws JsonProcessingException {
        String key = ORDER_DETAIL_KEY_PREFIX + orderDetail.getId();
        String json = redisObjectMapper.writeValueAsString(orderDetail);
        baseRedis.setProductWithExpiration(key, json, ORDER_DETAIL_CACHE_DURATION, TimeUnit.HOURS);
    }

    public List<OrderDetail> getOrderDetailsFromCache(Long orderId) throws JsonProcessingException {
        if (!useRedisCache) {
            return null;
        }
        String key = ORDER_DETAILS_KEY_PREFIX + orderId;
        String json = (String) baseRedis.getProduct(key);
        if (json != null) {
            return redisObjectMapper.readValue(json, new TypeReference<List<OrderDetail>>() {});
        }
        return null;
    }

    public void saveOrderDetailsToCache(Long orderId, List<OrderDetail> orderDetails) throws JsonProcessingException {
        String key = ORDER_DETAILS_KEY_PREFIX + orderId;
        String json = redisObjectMapper.writeValueAsString(orderDetails);
        baseRedis.setProductWithExpiration(key, json, ORDER_DETAIL_CACHE_DURATION, TimeUnit.HOURS);
    }

    public void clearOrderDetailCache(Long orderDetailId) {
        String key = ORDER_DETAIL_KEY_PREFIX + orderDetailId;
        baseRedis.deleteProduct(key);
    }

    public void clearOrderDetailsCache(Long orderId) {
        String key = ORDER_DETAILS_KEY_PREFIX + orderId;
        baseRedis.deleteProduct(key);
    }
} 