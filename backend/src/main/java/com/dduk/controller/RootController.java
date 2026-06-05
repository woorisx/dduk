package com.dduk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RootController {

    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }

    @GetMapping({"/frontend", "/frontend/", "/frontend/index.html"})
    public String frontendIndex() {
        return "redirect:/index.html";
    }
}
