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
import com.flipkart.ranger.model.ServiceNode;
import com.google.common.base.Strings;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import feign.ranger.client.ServiceDiscoveryClient;
import feign.ranger.common.ShardInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

/**
 * @author phaneesh
 */
@Slf4j
public class RangerTarget<T> implements Target<T> {

    @NonNull
    private final Class<T> type;

    @Getter
    private final String service;

    private final CuratorFramework curator;

    private ServiceDiscoveryClient client;

    private final String httpScheme;

    private final String fallbackUrl;

    private final String rootPathPrefix;

    public RangerTarget(final Class<T> type, final String environment, final String namespace, final String service,
                        final CuratorFramework curator, final boolean secured,
                        final ObjectMapper objectMapper) throws Exception {
        this(type, environment, namespace, service, curator, secured, null, objectMapper, null);
    }

    public RangerTarget(final Class<T> type, final String environment, final String namespace, final String service,
                        final CuratorFramework curator, final boolean secured, final String fallbackAddress,
                        final ObjectMapper objectMapper) throws Exception {
        this(type, environment, namespace, service, curator, secured, fallbackAddress, objectMapper, null);
    }

    @Builder
    private RangerTarget(final Class<T> type, final String environment, final String namespace, final String service,
                        final CuratorFramework curator, final boolean secured, final String fallbackAddress,
                        final ObjectMapper objectMapper, String rootPathPrefix) throws Exception {
        this.type = type;
        if (secured) {
            this.httpScheme = "https";
        } else {
            this.httpScheme = "http";
        }

        if (Strings.isNullOrEmpty(rootPathPrefix)) {
            this.rootPathPrefix = "";
        } else {
            this.rootPathPrefix = "/" + rootPathPrefix;
        }

        if (Strings.isNullOrEmpty(fallbackAddress)) {
            this.fallbackUrl = "";
        } else {
            this.fallbackUrl = String.format("%s://%s%s", this.httpScheme, fallbackAddress, this.rootPathPrefix);
        }

        this.service = service;
        this.curator = curator;
        client = ServiceDiscoveryClient.builder()
                .curator(curator)
                .environment(environment)
                .namespace(namespace)
                .serviceName(service)
                .objectMapper(objectMapper)
                .build();
        start();
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return service;
    }

    private String rangerUrl(ServiceNode<ShardInfo> node) {
        return String.format("%s://%s:%d%s", httpScheme, node.getHost(), node.getPort(), rootPathPrefix);
    }

    @Override
    public String url() {
        val node = client.getNode();
        if(node.isPresent()) {
            return rangerUrl(node.get());
        }
        if(Strings.isNullOrEmpty(fallbackUrl)) {
            throw new IllegalArgumentException("No service nodes found");
        }
        return fallbackUrl;
    }

    private void start() throws Exception {
        log.info("Starting service discovery client for {} on {}", service, curator.getZookeeperClient().getCurrentConnectionString());
        client.start();
        log.info("Started service discovery client for {} on {}", service, curator.getZookeeperClient().getCurrentConnectionString());
    }

    public Request apply(RequestTemplate input) {
        val node = client.getNode();
        if(node == null || !node.isPresent()) {
            throw new IllegalArgumentException("No service nodes found");
        }
        val url = rangerUrl(node.get());
        input.insert(0, url);
        return input.request();
    }
}
