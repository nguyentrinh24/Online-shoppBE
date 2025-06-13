package com.project.shopapp.Producers;

import com.project.shopapp.configurations.ConfigRabbitmq;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MessageProducer {
    private final RabbitTemplate rabbitTemplate;

    public void sendOrderMessage(String message) {
        rabbitTemplate.convertAndSend(
                ConfigRabbitmq.ORDER_EXCHANGE,
                ConfigRabbitmq.ORDER_ROUTING_KEY,
                message
        );
    }

    public void sendNotificationMessage(String message) {
        rabbitTemplate.convertAndSend(
                ConfigRabbitmq.NOTIFICATION_EXCHANGE,
                ConfigRabbitmq.NOTIFICATION_ROUTING_KEY,
                message
        );
    }

    // For backward compatibility
    public void sendMessage(String message) {
        sendNotificationMessage(message);
    }
}
