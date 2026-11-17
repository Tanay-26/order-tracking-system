package com.tracker.ordertracking.config;

import com.tracker.ordertracking.listener.OrderEventStreamListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Streams wiring - the durable, replayable event log of the system.
 *
 * Why a Stream and not plain Pub/Sub for state changes?
 * Pub/Sub is fire-and-forget: if no subscriber is alive at that exact moment,
 * the message is gone forever. A Stream stores every record on disk, tracks
 * per-consumer-group offsets, and keeps unacknowledged records in a Pending
 * Entries List so a crashed consumer can pick up exactly where it stopped.
 *
 * Status changes are business-critical -> Stream.
 * GPS pings are disposable (a new one arrives in 3s) -> Pub/Sub.
 */
@Configuration
@Slf4j
public class RedisStreamConfig {

    @Value("${app.redis.stream.key}")
    private String streamKey;

    @Value("${app.redis.stream.group}")
    private String consumerGroup;

    /**
     * A unique name per running instance, so two app instances form a real
     * consumer group and split the work instead of both processing everything.
     */
    @Bean
    public String streamConsumerName() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Spring starts this container automatically (it is a SmartLifecycle bean)
     * and stops it cleanly on shutdown.
     */
    @Bean(destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate redisTemplate,
            OrderEventStreamListener streamListener,
            String streamConsumerName) {

        createStreamAndGroupIfMissing(redisTemplate);

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>>
                options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .batchSize(50)                                  // read up to 50 records per poll
                .pollTimeout(Duration.ofMillis(200))            // block 200ms when the stream is empty
                .executor(new SimpleAsyncTaskExecutor("redis-stream-"))
                .errorHandler(throwable ->
                        log.error("Redis Stream container error: {}", throwable.getMessage(), throwable))
                .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        // ReadOffset.lastConsumed() == ">" : give me records nobody in my group has seen yet.
        container.receive(
                Consumer.from(consumerGroup, streamConsumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                streamListener);

        log.info("Redis Stream consumer ready. stream='{}' group='{}' consumer='{}'",
                streamKey, consumerGroup, streamConsumerName);
        return container;
    }

    /**
     * XGROUP CREATE fails if the stream key does not exist yet, which is always
     * the case on a fresh Redis container. So: create a throwaway record first,
     * then create the group from offset 0.
     */
    private void createStreamAndGroupIfMissing(StringRedisTemplate redisTemplate) {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(streamKey))) {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .in(streamKey)
                                .ofMap(Map.of("bootstrap", "true")));
                log.info("Created Redis stream '{}'", streamKey);
            }
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), consumerGroup);
            log.info("Created consumer group '{}' on stream '{}'", consumerGroup, streamKey);
        } catch (Exception e) {
            // BUSYGROUP: the group already exists. Perfectly normal on restart.
            log.info("Consumer group '{}' already exists on stream '{}' - reusing it", consumerGroup, streamKey);
        }
    }
}
