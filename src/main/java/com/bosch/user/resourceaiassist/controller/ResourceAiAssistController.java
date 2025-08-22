package com.bosch.user.resourceaiassist.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.*;

import java.util.Random;

@RestController
@CrossOrigin(origins = "*")
public class ResourceAiAssistController {
    private final DataSource dataSource;


    public ResourceAiAssistController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/hello")
    public String hello() {
        String[] responses = {
            "Hey team — backend is live and blazing! 🚀",
            "Hello world! Our API engine is humming—game on! ⚙️✨",
            "Good news, folks: servers up, endpoints firing—let’s roll! 🔥",
            "Hi squad — backend’s online and purring. 🐾",
            "Namaste team — backend green across the board! ✅",
            "Yo crew — mission control: backend lift-off! 🧑‍🚀",
            "Hey all — pipes open, data flowing—go time! 🌊",
            "Hello team — the stack’s awake and ready to serve. ⚡",
            "Hey champs — backend booted and battle-ready. 🛡️",
            "Greetings, legends — it’s alive! The backend is roaring! 🦁"
        };

        Random random = new Random();
        return responses[random.nextInt(responses.length)];
    }


    @GetMapping("/api/hana-check")
    public String checkHana() throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT CURRENT_USER, CURRENT_SCHEMA FROM DUMMY");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return "HANA OK → USER=" + rs.getString(1) + ", SCHEMA=" + rs.getString(2);
            }
            return "No result";
        }
    }
}