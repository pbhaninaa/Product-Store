package com.productstore.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.productstore.platform.controllers.PlatformApplication;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end smoke: boots full Spring context (DB, security, controllers) and hits key HTTP paths.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductStoreProjectIntegrationTest {

  @Autowired MockMvc mvc;

  @Test
  void contextLoads() {}

  @Test
  void healthEndpoint_isPublic() throws Exception {
    mvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));
  }

  @Test
  void merchantAdmin_withoutJwt_returns401() throws Exception {
    mvc.perform(
            put("/api/m/demo/admin/store/branding")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"storeName\":\"x\",\"shopType\":\"normal_store\"}"))
        .andExpect(status().isUnauthorized());
  }
}
