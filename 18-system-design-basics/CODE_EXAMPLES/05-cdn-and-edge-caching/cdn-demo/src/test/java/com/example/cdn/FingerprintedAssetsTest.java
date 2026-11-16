package com.example.cdn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceUrlProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FingerprintedAssetsTest {

    @Autowired MockMvc mvc;
    @Autowired ResourceUrlProvider urls;

    @Test
    void theAssetUrlContainsAHashOfItsContent() throws Exception {
        String versioned = urls.getForLookupPath("/js/app.js");
        System.out.println("versioned URL: " + versioned);

        assertThat(versioned).matches("/js/app-[0-9a-f]{32}\\.js");
        mvc.perform(get(versioned))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }

    @Test
    void theYearLongHeaderAlsoGoesOnTheUnversionedUrl() throws Exception {
        // spring.web.resources.cache.* applies to every static resource. A page that links /js/app.js
        // directly would keep the old file in browsers and CDNs for a year after a deploy.
        mvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public"));
    }
}
