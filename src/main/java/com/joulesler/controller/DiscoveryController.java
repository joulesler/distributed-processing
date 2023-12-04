package com.joulesler.controller;

import com.joulesler.config.URIConfig;
import com.joulesler.utils.SelfDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
public class DiscoveryController {

    @Autowired
    private SelfDiscovery selfDiscovery;

    @PostMapping(URIConfig.DISCOVER_URI)
    public ResponseEntity discoverInstances(@RequestBody SelfDiscovery.DiscoveryResponse request){
        return new ResponseEntity(selfDiscovery.updateDiscovered(request),HttpStatus.OK);
    }

}