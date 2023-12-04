package com.joulesler.service;

import com.joulesler.controller.LogicController;
import com.joulesler.utils.IRateProcessor;
import com.joulesler.utils.RateLimit;
import org.springframework.stereotype.Service;

@Service
public class LogicService implements IRateProcessor<LogicController.RequestObject, LogicController.ResponseObject> {

    @Override
    public void doLogic(RateLimit.QueueRequest<LogicController.RequestObject, LogicController.ResponseObject> queueRequest){
        queueRequest.getResponse().completeAsync(()-> {
            try {
                Thread.sleep(5000);
            } catch (Exception ex){

            }
            return new LogicController.ResponseObject(queueRequest.getRequest().getSomeData());
        });
    }
}