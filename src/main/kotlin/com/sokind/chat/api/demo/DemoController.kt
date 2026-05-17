package com.sokind.chat.api.demo

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/demo")
class DemoController {

    @GetMapping
    fun index(): String = "chat"
}
