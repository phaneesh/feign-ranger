/*
 * Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.ranger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import feign.*;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import org.apache.curator.test.TestingCluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
@Slf4j
public class FeignRangerFallbackAddressTest {

    private static String FALLBACK_ADDRESS = "fallback-address:9999";

    private static String ROOT_PATH_PREFIX = "apis/ks";

    private TestingCluster testingCluster;

    private CuratorFramework curator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Logger.ErrorLogger logger = new Logger.ErrorLogger();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(9999);

    @Before
    public void startTestCluster() throws Exception {

        testingCluster = new TestingCluster(1);
        testingCluster.start();
        curator = CuratorFrameworkFactory.builder()
                .connectString(testingCluster.getConnectString())
                .namespace("test")
                .retryPolicy(new RetryForever(3000))
                .build();
        curator.start();
    }

    @After
    public void stopTestCluster() throws Exception {
        if(null != curator) {
            curator.close();
        }
        if(null != testingCluster) {
            testingCluster.close();
        }

    }

    @Test(expected = IllegalArgumentException.class)
    public void testFallbackApplyCallFail() throws Exception {
        stubFor(get(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(objectMapper.writeValueAsBytes(
                                TestResponse.builder()
                                        .message("test")
                                        .build()
                        ))
                        .withHeader("Content-Type", "application/json")));
        TestApi api = Feign.builder()
                .decoder(new JacksonDecoder())
                .encoder(new JacksonEncoder())
                .logger(logger)
                .logLevel(Logger.Level.FULL)
                .target(RangerTarget.<TestApi>builder().type(TestApi.class).environment("test").namespace("test").service("test").curator(curator).objectMapper(objectMapper).fallbackAddress("127.0.0.1:9999").build());
        val result = api.test();
        assertTrue(result.message.equalsIgnoreCase("test"));
    }

    @Test
    public void testFallbackUrlCall() throws Exception {
        val target = RangerTarget.<TestApi>builder().type(TestApi.class).environment("test").namespace("test").service("test").curator(curator).objectMapper(objectMapper).fallbackAddress(FALLBACK_ADDRESS).build();
        assertEquals("http://" + FALLBACK_ADDRESS, target.url());
    }

    @Test
    public void testFallbackrootPathPrefixUrlCall() throws Exception {
        val target = RangerTarget.<TestApi>builder().type(TestApi.class).environment("test").namespace("test").service("test").curator(curator).objectMapper(objectMapper).fallbackAddress(FALLBACK_ADDRESS).rootPathPrefix(ROOT_PATH_PREFIX).build();
        assertEquals("http://" + FALLBACK_ADDRESS + "/" + ROOT_PATH_PREFIX, target.url());
    }

    @Test
    public void testFallbackrootPathPrefixHttpsUrlCall() throws Exception {
        val target = RangerTarget.<TestApi>builder().type(TestApi.class).environment("test").namespace("test").service("test").curator(curator).secured(true).objectMapper(objectMapper).fallbackAddress(FALLBACK_ADDRESS).rootPathPrefix(ROOT_PATH_PREFIX).build();
        assertEquals("https://" + FALLBACK_ADDRESS + "/" + ROOT_PATH_PREFIX, target.url());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    static class TestResponse {

        private String message;
    }

    interface TestApi {

        @RequestLine("GET /v1/test")
        TestResponse test();
    }
}
