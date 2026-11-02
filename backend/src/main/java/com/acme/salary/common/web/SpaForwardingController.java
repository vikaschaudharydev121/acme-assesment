package com.acme.salary.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Deep links into the Angular app (e.g. a refresh on /employees) must return index.html. */
@Controller
class SpaForwardingController {

    @GetMapping({"/login", "/employees", "/employees/**", "/insights", "/insights/**"})
    String forwardToSpa() {
        return "forward:/index.html";
    }
}
