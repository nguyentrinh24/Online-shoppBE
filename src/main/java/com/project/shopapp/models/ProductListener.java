package com.project.shopapp.models;

import com.project.shopapp.services.Product.IProductRedisService;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EntityListeners(ProductListener.class)
public class ProductListener {

    private static final Logger logger = LoggerFactory.getLogger(ProductListener.class);

    private final IProductRedisService productRedisService;

    @PrePersist
    public void prePersist(Product product) {
        logger.info("prePersist");
    }

    @PostPersist
    public void postPersist(Product product) {
        logger.info("postPersist");
        productRedisService.clear();
    }

    @PreUpdate
    public void preUpdate(Product product) {
        logger.info("preUpdate");
    }

    @PostUpdate
    public void postUpdate(Product product) {
        logger.info("postUpdate");
        productRedisService.clear();
    }

    @PreRemove
    public void preRemove(Product product) {
        logger.info("preRemove");
    }

    @PostRemove
    public void postRemove(Product product) {
        logger.info("postRemove");
        productRedisService.clear();
    }
}
