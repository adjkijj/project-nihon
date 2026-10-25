package com.nihon.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("title", "Project Nihon - Ứng Dụng Ghi Nhớ Từ Vựng Tiếng Nhật");
        model.addAttribute("statusMessage", "Hệ thống nền tảng đã sẵn sàng.");
        return "index";
    }
}
