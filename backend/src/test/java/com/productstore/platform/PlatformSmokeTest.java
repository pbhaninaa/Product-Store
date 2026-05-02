package com.productstore.platform;

import com.productstore.platform.controllers.PlatformApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = PlatformApplication.class)
@ActiveProfiles("test")
class PlatformSmokeTest {
  @Test
  void contextLoads() {}
}

