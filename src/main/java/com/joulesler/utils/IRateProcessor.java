package com.joulesler.utils;

@FunctionalInterface
public interface IRateProcessor<T, E> {
    void doLogic(RateLimit.QueueRequest<T, E> request);
}

