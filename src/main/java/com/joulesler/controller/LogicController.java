package com.joulesler.controller;

import com.joulesler.service.LogicService;
import com.joulesler.utils.RateLimit;
import com.joulesler.utils.SelfDiscovery;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Controller
public class LogicController {

    @Autowired
    private LogicService logicService;

    @Autowired
    private SelfDiscovery selfDiscovery;

    RateLimit<RequestObject, ResponseObject> rateLimit = new RateLimit<>(5, 5, logicService, selfDiscovery);

    @PostMapping("/doLogic")
    public ResponseEntity doLogic(@RequestBody RequestObject request){
        CompletableFuture<ResponseObject> queueObject =  rateLimit.addRequest(request);
        ResponseObject response;
        try {
            response = queueObject.get();
        } catch (InterruptedException e) {
            // Handle internal errors
            return new ResponseEntity(HttpStatus.I_AM_A_TEAPOT);
        } catch (ExecutionException e) {
            // Handle rate limiting errors
            return new ResponseEntity(HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);
        }
        // Return successful respose
        return new ResponseEntity(response, HttpStatus.OK);
    }

    @Getter
    @Setter
    public static class RequestObject{
        private String someData;
    }

    @Getter
    @Setter
    public static class ResponseObject{
        private String doneData;

        public ResponseObject(String _doneData){
            this.doneData = _doneData;
        }
    }
}
