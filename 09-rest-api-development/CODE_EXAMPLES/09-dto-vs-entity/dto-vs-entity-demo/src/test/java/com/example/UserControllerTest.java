package com.example;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class UserControllerTest {

    @Autowired
    WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void responseDoesNotIncludePasswordHash() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"hunter2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .as("response body must never contain the password hash")
                .doesNotContain("passwordHash")
                .doesNotContain("hashed::");
    }

    @Test
    void serverManagedFieldsInRequestBodyAreIgnored() throws Exception {
        // Client tries to set passwordHash and createdAt -- the DTO ignores them
        // (they are not fields on CreateUserRequest, so Jackson drops them).
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bob\","
                                + "\"password\":\"x\","
                                + "\"passwordHash\":\"evil-supplied-hash\","
                                + "\"createdAt\":\"1970-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                // createdAt is the server's time, not 1970.
                .andExpect(jsonPath("$.createdAt", Matchers.not(Matchers.startsWith("1970"))));
    }

    @Test
    void readBackHidesHash() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"carol\",\"password\":\"s3cret\"}"))
                .andReturn();

        String location = created.getResponse().getHeader("Location");
        // Strip scheme+host, leaving the path so MockMvc can replay it.
        String path = java.net.URI.create(location).getPath();

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("passwordHash"))));
    }

    @Test
    void unknownUserReturns404() throws Exception {
        mockMvc.perform(get("/api/users/9999"))
                .andExpect(status().isNotFound());
    }
}
