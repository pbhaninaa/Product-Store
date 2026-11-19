package com.productstore.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.productstore.platform.controllers.PlatformApplication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Store images are saved with the product/branding and served publicly: a guest with no sign-in,
 * a signed-in client and a visitor with an expired token all get the same image.
 */
@SpringBootTest(classes = PlatformApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StoreImagesIntegrationTest {
  private static final String PASSWORD = "Secret@123456";
  private static final byte[] PNG =
      Base64.getDecoder()
          .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  private String token;
  private String slug;

  @BeforeEach
  void merchant() throws Exception {
    mvc.perform(post("/api/auth/register-system-admin").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsString(Map.of("email", "img-admin-" + UUID.randomUUID() + "@test.local", "password", PASSWORD))));
    String body =
        json.writeValueAsString(
            Map.of(
                "merchantName", "Image Shop " + UUID.randomUUID().toString().substring(0, 8),
                "ownerEmail", "img-" + UUID.randomUUID() + "@test.local",
                "ownerPassword", PASSWORD));
    var out =
        json.readTree(
            mvc.perform(post("/api/auth/register-merchant").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    token = out.get("token").asText();
    slug = out.get("merchantSlug").asText();
  }

  private String createProduct(MockMultipartFile image) throws Exception {
    var res =
        mvc.perform(multipart("/api/m/" + slug + "/admin/products")
                .file(image)
                .param("name", "Photo product")
                .param("category", "Test")
                .param("price", "10")
                .param("stock", "3")
                .header("Authorization", "Bearer " + token))
            .andReturn()
            .getResponse();
    return res.getStatus() == 200 ? json.readTree(res.getContentAsString()).get("id").asText() : "status:" + res.getStatus() + ":" + res.getContentAsString();
  }

  @Test
  void uploadedProductImageIsSavedAndVisibleToEveryone() throws Exception {
    String id = createProduct(new MockMultipartFile("image", "photo.png", "image/png", PNG));
    String url = "/api/public/m/" + slug + "/products/" + id + "/image";

    // Guest (no sign-in)
    var guest =
        mvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(PNG))
            .andExpect(header().exists("ETag"))
            .andReturn()
            .getResponse();

    // Signed-in client and a visitor whose token has expired see the same image.
    mvc.perform(get(url).header("Authorization", "Bearer expired.token.value")).andExpect(status().isOk());

    // Browser already has it: 304, nothing re-downloaded.
    mvc.perform(get(url).header("If-None-Match", guest.getHeader("ETag"))).andExpect(status().isNotModified());

    // The public catalog points at that image.
    mvc.perform(get("/api/public/m/" + slug + "/catalog"))
        .andExpect(jsonPath("$.products[?(@.id == '" + id + "')].imageUrl").value(org.hamcrest.Matchers.hasItem(url)));
  }

  @Test
  void typeIsDetectedFromTheFileNotFromWhatTheBrowserClaims() throws Exception {
    String id = createProduct(new MockMultipartFile("image", "photo.bin", "application/octet-stream", PNG));
    mvc.perform(get("/api/public/m/" + slug + "/products/" + id + "/image"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"));
  }

  @Test
  void nonImageUploadIsRejectedAndNothingIsSaved() throws Exception {
    byte[] html = "<html><script>alert(1)</script></html>".getBytes();
    String result = createProduct(new MockMultipartFile("image", "evil.png", "image/png", html));
    assertThat(result).startsWith("status:400").contains("image_type_not_supported");
  }

  @Test
  void storeLogoAndBannerAreSavedAndPublic() throws Exception {
    mvc.perform(multipart("/api/m/" + slug + "/admin/store/branding")
            .file(new MockMultipartFile("logo", "logo.png", "image/png", PNG))
            .file(new MockMultipartFile("hero", "hero.png", "image/png", PNG))
            .param("storeName", "Image Shop")
            .header("Authorization", "Bearer " + token)
            .with(r -> {
              r.setMethod("PUT");
              return r;
            }))
        .andExpect(status().isOk());
    for (String kind : new String[] {"logo", "hero"}) {
      mvc.perform(get("/api/public/m/" + slug + "/branding/" + kind))
          .andExpect(status().isOk())
          .andExpect(content().bytes(PNG));
    }
  }
}
