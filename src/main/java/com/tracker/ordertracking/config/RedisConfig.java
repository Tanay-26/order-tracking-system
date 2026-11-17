package com.tracker.ordertracking.config;

import com.tracker.ordertracking.listener.RedisPubSubListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Redis Pub/Sub wiring.
 *
 * Every application instance subscribes to the same two channels. When ANY
 * instance publishes an update, ALL instances receive it and forward it to the
 * customers connected to their own WebSocket sessions. That is what makes the
 * system horizontally scalable: a customer connected to instance-B still sees
 * an update produced by instance-A.
 */
@Configuration
@Slf4j
public class RedisConfig {

    @Value("${app.redis.channel.order-status}")
    private String orderStatusChannel;

    @Value("${app.redis.channel.rider-location}")
    private String riderLocationChannel;

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       RedisPubSubListener pubSubListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setTaskExecutor(pubSubExecutor());

        container.addMessageListener(pubSubListener, new ChannelTopic(orderStatusChannel));
        container.addMessageListener(pubSubListener, new ChannelTopic(riderLocationChannel));

        log.info("Subscribed to Redis Pub/Sub channels: [{}, {}]", orderStatusChannel, riderLocationChannel);
        return container;
    }

    /**
     * Dedicated pool for fan-out work. If Pub/Sub delivery ran on the Tomcat
     * threads, a slow WebSocket client could stall incoming HTTP requests.
     */
    @Bean
    public ThreadPoolTaskExecutor pubSubExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("redis-pubsub-");
        executor.initialize();
        return executor;
    }
}
