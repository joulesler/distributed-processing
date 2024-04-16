# Distributed Processing for Java Services

This is a library that helps to manage multiple instances of a service. It assumes an evenly distributed load-balancer lies before the services that use this library. 

This accomplishes two things:

 1. Instance awareness. The number of instances of a services can be known and queried from any one of the nodes.
 2. Rate Limiting. The number of instances can be used to limit the rate at which each of the instances processes a request, for any number of downstream services. 

# 1. Instance Awareness
All instances will discover other instances of the same service type.

## Gossip Protocol

Gossip protocol relies on the instance knowing its own private IP (assuming that all instances are on the same subnet). Its own IP and all previously known same-service types are broadcast. There is a TTL set for the liveliness of the networks nodes, and a broadcast will be done before the TTL timeout expires.

# 2. Rate-Limiting

Each instance has a distributed queue, in each queue there is a queue length in milliseconds, between each of the requests. 

## Internal Queue
The internal queue can be configured to buffer a number of responses, prior to sending to downstream services. If the queue size is exceeded, the subsequent requests will be rejected immediately.

## Synchronous Handling

The requests are handled synchronously, meaning that the caller will await for a HTTP response even while the queue is buffering.
