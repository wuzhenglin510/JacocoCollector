package com.gt.jacoco.api;

import com.gt.jacoco.entity.RegisterInfo;
import com.gt.jacoco.entity.RegisterRequest;
import com.gt.jacoco.task.CoverageWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "register")
public class RegisterController {

    @Autowired
    private CoverageWorker coverageWorker;

    @PostMapping
    public RegisterInfo register(@RequestBody @Validated RegisterRequest body) {
        return coverageWorker.register(body);
    }

}
