package com.project.shopapp.services.Product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.shopapp.redis.BaseRedis;
import com.project.shopapp.responses.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ProductRedisService implements IProductRedisService {

    private final BaseRedis baseRedis;
    private final ObjectMapper redisObjectMapper;
    
    @Value("${spring.data.redis.use-redis-cache}")
    private boolean useRedisCache;
    
    private static final long PRODUCT_LIST_CACHE_DURATION = 1; // 1 hour

    private String getKeyFrom(String keyword,
                              Long categoryId,
                              PageRequest pageRequest) {
        int pageNumber = pageRequest.getPageNumber();
        int pageSize = pageRequest.getPageSize();
        Sort sort = pageRequest.getSort();
        String sortDirection = sort.getOrderFor("id")
                .getDirection() == Sort.Direction.ASC ? "asc" : "desc";
        String key = String.format("all_products:%s:%d:%d:%d:%s",
                keyword, categoryId, pageNumber, pageSize, sortDirection);
        return key;
    }

    @Override
    public void clear() {

    }

    @Override
    public List<ProductResponse> getAllProducts(String keyword, Long categoryId, PageRequest pageRequest) throws JsonProcessingException {
        if (!useRedisCache) {
            return null;
        }
        String key = this.getKeyFrom(keyword, categoryId, pageRequest);
        String json = (String) baseRedis.getProduct(key);
        
        if (json != null) {
            return redisObjectMapper.readValue(json, new TypeReference<List<ProductResponse>>() {});
        }
        return null;
    }

    @Override
    public void saveAllProducts(List<ProductResponse> productResponses, String keyword, Long categoryId, PageRequest pageRequest) throws JsonProcessingException {
        String key = this.getKeyFrom(keyword, categoryId, pageRequest);
        String json = redisObjectMapper.writeValueAsString(productResponses);
        baseRedis.setProductWithExpiration(key, json, PRODUCT_LIST_CACHE_DURATION, TimeUnit.HOURS);
    }
}
