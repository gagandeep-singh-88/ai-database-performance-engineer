package com.dbperf;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DbPerfApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full Spring context (security, JPA, JWT config) wires up.
    }
}
