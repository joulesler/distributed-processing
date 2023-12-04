package com.joulesler.utils;

import com.joulesler.config.URIConfig;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @implNote To discover the number of nodes in the network.
 * @var thisUUD is the unique identifier for this node, which can be changed to IP address.
 * Each call out to neighbouring nodes has a 1/n chance of hitting itself.
 * Where n is the number of nodes in the network (unknown value)
 *
 */

@Slf4j
@Component
public class SelfDiscovery implements IGetInstances{

    @Autowired
    private WebClientCaller webClientCaller;

    @Value("${server.discover.dns}")
    private String serverDns;

    @Value("${server.discover.port}")
    private int serverPort;

    @Value("${server.discover.interval}")
    private String interval;


    // Unique UUID for this instance
    private String thisUUID = UUID.randomUUID().toString();

    // Map of all instances -> recent active timestamps
    private Map<String, Long> currentInstanceMap = new ConcurrentHashMap<>();

    // External calling service
    private WebClient webClient;

    // History of instances discovered
    private LinkedList<Integer> discoverRate;

    /**
     * @implNote To create a discovery of all services in the dns
     */
    @PostConstruct
    public void discoverServiceCount(){
        this.webClient = webClientCaller.getWebClient(serverDns + ":" + serverPort);
        this.currentInstanceMap.put(thisUUID, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
//        this.discoverServices();
    }

    /**
     * @implNote to poll other instances to discover service count
     */
    @Scheduled(fixedRateString = "${server.discover.interval}")
    public void discoverServices() {
        // Update own timestamp before sending out
        this.currentInstanceMap.put(thisUUID, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
        DiscoveryResponse request = new DiscoveryResponse(currentInstanceMap);
        DiscoveryResponse response = webClient.post().uri(uriBuilder -> uriBuilder.path(
                        URIConfig.DISCOVER_URI).build())
                .body(BodyInserters.fromValue(request
                ))
                .retrieve()
                .bodyToMono(DiscoveryResponse.class)
                .block();

        //continuousCheck(response);

        for (Map<String, Long> requestListEntry: response.instancesDiscovered){
            for (Map.Entry<String, Long> incomingData: requestListEntry.entrySet()){
                if (currentInstanceMap.getOrDefault(incomingData.getKey(), 0L) < incomingData.getValue()){
                    currentInstanceMap.put(incomingData.getKey(), incomingData.getValue());
                }
            }
        }


        // TODO: Remove instances if they are more than x/n time in idle.
        // TODO: If the same instance id comes back and there is only one, then send another call out to discover Still WIP to consider single instance deployments
    }

    /**
     * @implNote When other instances call to discover this instance
     * @param request
     * @return
     */
    public DiscoveryResponse updateDiscovered(DiscoveryResponse request){
        // If there are more instances in the local copy than in the inbound copy, should schedule another call to update.
        // If there are more calls into the service than there are out, to call out
        List responseList = new ArrayList<HashMap<String,Long>>();

        // Update own timestamp before sending out
        this.currentInstanceMap.put(thisUUID, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));

        //continuousCheck(request);

        // Update local copy, adding any new instances
        for (Map<String, Long> requestListEntry: request.instancesDiscovered){
            for (Map.Entry<String, Long> incomingData: requestListEntry.entrySet()){
                if (currentInstanceMap.getOrDefault(incomingData.getKey(), 0L) < incomingData.getValue()){
                    currentInstanceMap.put(incomingData.getKey(), incomingData.getValue());
                }
            }

        }

//        for (int i= 0; i< request.instancesDiscovered.size(); i++) {
//            // Only update if the request is more recent or if there is no entry in the map (default)
//            if (instanceMap.getOrDefault(request.instancesDiscovered.get(i).keySet().iterator(), 0L) < request.instancesDiscovered.get(i).getT2()){
//                instanceMap.put(request.instancesDiscovered.get(i).getT1(), request.instancesDiscovered.get(i).getT2());
//            }
//        }

        // Update response to other instances
        for (Map.Entry<String, Long> entry : currentInstanceMap.entrySet()) {
            responseList.add(Collections.singletonMap(entry.getKey(), entry.getValue()));
        }

        request.setInstancesDiscovered(responseList);

        return request;
    }

    @Override
    public int getValidInstances(){
        int validInstances = 0;
        Long timestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        for (Map.Entry<String, Long> entry : currentInstanceMap.entrySet()) {
            if (timestamp - entry.getValue() < 2 * Integer.valueOf(interval)/1000) // Convert to seconds from milis
                validInstances++;
        }
        return validInstances;
    }

    /**
     * <p>
     *     For each call out to neighbour, check which nodes are new, and how many new nodes are found.
     *     Ceil of the difference to be taken until tend to zero
     *     Taking into account last 3 calls
     * </p>
     * @return
     */
    public void continuousCheck(DiscoveryResponse response) {
        discoverRate.push(response.getInstancesDiscovered().size() - currentInstanceMap.size());
        // Save the rate of nodes discovered per round (either calling in or out)
        if (discoverRate.size() > 3){
            discoverRate.remove(3);
        }

        // if still in discovery
        for (Integer discovered : discoverRate){
            if (discovered != 0) {
                CompletableFuture.runAsync(() -> {
                    discoverServices();
                });
                break;
            }
        }
    }

    protected String getLocalIpAddress()  {
        try (final DatagramSocket datagramSocket = new DatagramSocket()) {
            datagramSocket.connect(InetAddress.getByName("8.8.8.8"), 12345);
            return datagramSocket.getLocalAddress().getHostAddress();
        } catch (IOException ioEx) {
            log.error("Could not find IP Address: ", ioEx.getLocalizedMessage());
        }
        return "";
    }

    @Getter
    @Setter
    public static class DiscoveryResponse {
        public DiscoveryResponse(){}

        public DiscoveryResponse(String id, Long timestamp){
            Map<String, Long> map = new HashMap<>();
            map.put(id, timestamp);
            this.setInstancesDiscovered(Collections.singletonList(map));
        }

        public DiscoveryResponse(Map<String, Long> instanceMap){
            instancesDiscovered = new ArrayList<Map<String,Long>>();
            Map<String, Long> map = new HashMap<>();

            for (Map.Entry<String, Long> entry : instanceMap.entrySet()) {
                map.put(entry.getKey(), entry.getValue());
            }

        }
        private List<Map<String, Long>> instancesDiscovered;
        private String requestId;

    }
}