package com.example.cdn;

import com.example.cdn.EdgeCache.Request;
import com.example.cdn.EdgeCache.Response;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** The origin is the real Spring app, called through MockMvc; the EdgeCache sits in front of it. */
@SpringBootTest
@AutoConfigureMockMvc
abstract class EdgeTestBase {

    @Autowired MockMvc mvc;
    @Autowired ShopController shop;

    EdgeCache edge;

    @BeforeEach
    void newEdge() {
        shop.reset();
        edge = new EdgeCache(this::callOrigin);
    }

    Response callOrigin(Request request) {
        try {
            MockHttpServletRequestBuilder builder = get(request.path());
            request.headers().forEach((name, value) -> {
                if (name.equals("Cookie")) {   // MockMvc doesn't turn a raw Cookie header into cookies
                    String[] kv = value.split("=", 2);
                    builder.cookie(new Cookie(kv[0], kv[1]));
                } else {
                    builder.header(name, value);
                }
            });
            MockHttpServletResponse response = mvc.perform(builder).andReturn().getResponse();
            Map<String, String> headers = new HashMap<>();
            for (String name : response.getHeaderNames()) headers.put(name, String.join(", ", response.getHeaders(name)));
            return new Response(response.getStatus(), headers, response.getContentAsString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Request as(String user, String path) {
        return new Request(path, Map.of("Cookie", "session=" + user));
    }
}
