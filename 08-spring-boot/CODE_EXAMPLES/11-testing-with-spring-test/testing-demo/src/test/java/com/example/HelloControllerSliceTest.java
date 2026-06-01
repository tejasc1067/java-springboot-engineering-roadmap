package com.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice test: boots only the web layer (DispatcherServlet + this controller).
 * No @Service beans are scanned. We supply HelloService as a Mockito mock via
 * @MockitoBean so the controller has its dependency satisfied.
 *
 * Far faster than @SpringBootTest because most autoconfiguration is skipped.
 */
@WebMvcTest(HelloController.class)
class HelloControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    HelloService helloService;

    @Test
    void getHelloDelegatesToHelloService() throws Exception {
        when(helloService.greet()).thenReturn("Mocked greeting");

        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Mocked greeting"));
    }
}
