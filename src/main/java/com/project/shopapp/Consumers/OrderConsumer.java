package com.project.shopapp.Consumers;

import com.project.shopapp.configurations.ConfigRabbitmq;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrderConsumer {
    private static final Logger logger = LoggerFactory.getLogger(OrderConsumer.class);

    @RabbitListener(queues = ConfigRabbitmq.ORDER_QUEUE)
    public void processOrder(String message) {
        try {
            logger.info("Processing order: {}", message);
            // TODO: Add your order processing logic here
            // For example:
            // 1. Update order status
            // 2. Send confirmation email
            // 3. Update inventory
            // 4. Generate invoice
        } catch (Exception e) {
            logger.error("Error processing order: {}", e.getMessage());
            // TODO: Implement error handling and retry logic
        }
    }
} 