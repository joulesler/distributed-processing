package com.joulesler.utils;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @param <T> Request
 * @param <E> Response
 */
@Slf4j
public class RateLimit<T, E> {

    /**
     * Structure
     * (First)              (Last)
     * (1) -> (2) -> (3) -> (4) (Completed))
     * Process n only when n-1 is completed
     * <p>
     * schedule when (2) can run based on (1)
     * <p>
     * Functions
     * 1. Add request to queue
     * 2. Consume from queue
     * 2a. Set speed/ delay of consumption from queue
     * 3. Return completable future response in queue
     */

    @Getter
    @Setter
    // Thread safe doubly linked list
    private LinkedBlockingDeque<QueueRequest<T, E>> requestQueue;

    @Getter
    private Integer rate;

    @Getter
    private Integer queueSize;

    private IRateProcessor downstream;

    private IGetInstances discovery;

    private Long lastCompleteMilis = System.currentTimeMillis();

    public RateLimit(Integer _rate, Integer _queueSize, IRateProcessor _downstream, IGetInstances _discovery) {
        requestQueue = new LinkedBlockingDeque<>();
        queueSize = _queueSize;
        rate = _rate;
        downstream = _downstream;
        discovery = _discovery;
    }

    /**
     * Can force override the processing of a message in the queue, by calling this method.
     *
     * @param downstream
     * @param request
     */
    public void consume(IRateProcessor downstream, QueueRequest request) {
        // To solve for period of inactivity, take the highest value
        lastCompleteMilis = request.getStartTime() > System.currentTimeMillis() ? request.getStartTime() : System.currentTimeMillis();

        Long delay = request.startTime - System.currentTimeMillis();

        log.info("Start time is: {}, current time is: {}, Delay is {}", request.startTime, System.currentTimeMillis(), delay);
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
            requestQueue.remove(request);
            downstream.doLogic(request);
        });
    }

    public CompletableFuture addRequest(T request) {
        QueueRequest element;
        // Add request to the queue if new
        // delay in mS of consuming each message, in this service specifically
        Long lagTime = (Long.valueOf(discovery.getValidInstances()) * 1000L) / Long.valueOf(rate);
        // Compute duration between each of the elements in the queue, in milliseconds
        if (!requestQueue.isEmpty()) {
            Long nextStart = requestQueue.getLast().startTime + lagTime;
            element = new QueueRequest(request, nextStart);
            if (requestQueue.size() < queueSize) {
                /**
                 * To store the latest elements
                 */
                requestQueue.add(element);
                consume(downstream, element);
            } else {
                element.response.completeExceptionally(new IOException("Rate limit exceeded"));
            }
            log.info("Message is queued for start at {}, {} messages in queue", nextStart, requestQueue.size());

        } else {
            if ((lastCompleteMilis + lagTime) > System.currentTimeMillis()) {
                // If new request
                element = new QueueRequest(request, lastCompleteMilis + lagTime);
            } else {
                element = new QueueRequest(request);
            }
            log.info("Last scheduled task was at: {}", lastCompleteMilis);
            log.info("Message is queued for completion at {}, {} messages in queue", element.startTime, requestQueue.size());
            requestQueue.add(element);
            consume(downstream, element);
        }

        return element.response;
    }

    /**
     * @param <T> Request
     * @param <E> Response
     */
    @Getter
    @Setter
    public static class QueueRequest<T, E> {
        String uuid;
        Long insertTime;
        Long startTime;
        Long completionTime;
        T request;
        CompletableFuture<E> response;

        /**
         * @param _request Original request, to be started immediately
         */
        QueueRequest(T _request) {
            this.uuid = UUID.randomUUID().toString();
            this.request = _request;
            this.insertTime = System.currentTimeMillis();
            this.startTime = System.currentTimeMillis();
            response = new CompletableFuture<>();
        }

        /**
         * @param _request   Original request
         * @param _startTime when to start this request
         */
        QueueRequest(T _request, Long _startTime) {
            this.uuid = UUID.randomUUID().toString();
            this.request = _request;
            this.insertTime = System.currentTimeMillis();
            this.startTime = _startTime;
            response = new CompletableFuture<>();
        }
    }
}