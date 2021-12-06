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
import com.flipkart.ranger.ServiceProviderBuilders;
import com.flipkart.ranger.healthcheck.Healthcheck;
import com.flipkart.ranger.healthcheck.HealthcheckStatus;
import com.flipkart.ranger.serviceprovider.ServiceProvider;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.Lists;
import feign.*;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.ranger.common.ShardInfo;
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

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

/**
 * Unit test for simple App.
 */
@Slf4j
public class FeignRangerHttpTest {

    private TestingCluster testingCluster;

    private List<Healthcheck> healthchecks = Lists.newArrayList();
    private ServiceProvider<ShardInfo> serviceProvider;

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
        serviceProvider = ServiceProviderBuilders.<ShardInfo>shardedServiceProviderBuilder()
                .withCuratorFramework(curator)
                .withNamespace("test")
                .withServiceName("test")
                .withSerializer(data -> {
                    try {
                        return objectMapper.writeValueAsBytes(data);
                    } catch (Exception e) {
                        log.warn("Could not parse node data", e);
                    }
                    return null;
                })
                .withHostname("127.0.0.1")
                .withPort(9999)
                .withNodeData(ShardInfo.builder()
                        .environment("test")
                        .build())
                .withHealthcheck(() -> {
                    for(Healthcheck healthcheck : healthchecks) {
                        if(HealthcheckStatus.unhealthy == healthcheck.check()) {
                            return HealthcheckStatus.unhealthy;
                        }
                    }
                    return HealthcheckStatus.healthy;
                })
                .buildServiceDiscovery();
        serviceProvider.start();
    }

    @After
    public void stopTestCluster() throws Exception {
        if(null != serviceProvider ) {
            serviceProvider.stop();
        }
        if(null != curator) {
            curator.close();
        }
        if(null != testingCluster) {
            testingCluster.close();
        }

    }

    @Test
    public void testSuccessfulHttpCall() throws Exception {
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
                .target(new RangerTarget<>(TestApi.class, "test", "test", "test", curator, false, null, objectMapper));
        val result = api.test();
        assertTrue(result.message.equalsIgnoreCase("test"));
    }

    @Test
    public void testSuccessfulHttpRootPathPrefixCall() throws Exception {
        stubFor(get(urlEqualTo("/apis/ks/v1/test"))
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
                .target(RangerTarget.<TestApi>builder().type(TestApi.class).environment("test").namespace("test").service("test").curator(curator).objectMapper(objectMapper).rootPathPrefix("apis/ks").build());
        val result = api.test();
        assertTrue(result.message.equalsIgnoreCase("test"));
    }

    @Test
    public void testFailureHttpCall() throws Exception {
        stubFor(get(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(500)));
        TestApi api = Feign.builder()
                .decoder(new JacksonDecoder())
                .encoder(new JacksonEncoder())
                .logger(logger)
                .logLevel(Logger.Level.FULL)
                .target(new RangerTarget<>(TestApi.class, "test", "test", "test", curator, false, null, objectMapper));
        try {
            api.test();
            fail("Should have failed!");
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue(e instanceof FeignException);
            assertEquals(500, ((FeignException)e).status());
        }
    }

    @Test
    public void testTimeoutHttpCall() throws Exception {
        stubFor(get(urlEqualTo("/v1/test"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withFixedDelay(2000))
                );
        TestApi api = Feign.builder()
                .decoder(new JacksonDecoder())
                .encoder(new JacksonEncoder())
                .options(new Request.Options(100, 100))
                .logger(logger)
                .logLevel(Logger.Level.FULL)
                .target(new RangerTarget<>(TestApi.class, "test", "test", "test", curator, false, null, objectMapper));
        try {
            api.test();
            fail("Should have failed!");
        } catch (Exception e) {
            assertTrue(e instanceof RetryableException);
        }
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
