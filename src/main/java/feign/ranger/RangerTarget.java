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
import com.google.common.base.Joiner;
import feign.Request;
import feign.RequestTemplate;
import feign.Target;
import feign.ranger.client.ServiceDiscoveryClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

/**
 * @author phaneesh
 */
@Slf4j
public class RangerTarget<T> implements Target<T> {

    private final Class<T> type;

    private final String namespace;

    private final String name;

    @Getter
    private final String service;

    private ServiceDiscoveryClient client;

    private final boolean secured;

    public RangerTarget(final Class<T> type, final String environment, final String namespace, final String service,
                        final CuratorFramework curator, final boolean secured, final ObjectMapper objectMapper) throws Exception {
        this.type = type;
        this.namespace = namespace;
        this.secured = secured;
        this.service = service;
        this.name = Joiner.on('.').join(namespace, service);
        client = ServiceDiscoveryClient.builder()
                .curator(curator)
                .environment(environment)
                .namespace(namespace)
                .serviceName(service)
                .objectMapper(objectMapper)
                .build();
        log.debug("Starting service discovery client for {} on {}", service, curator.getZookeeperClient().getCurrentConnectionString());
        client.start();
        log.debug("Started service discovery client for {} on {}", service, curator.getZookeeperClient().getCurrentConnectionString());
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String url() {
        val node = client.getNode();
        if(!node.isPresent()) {
            throw new IllegalArgumentException("No service nodes found");
        }
        return String.format("%s://%s:%d", secured ? "https" : "http", node.get().getHost(), node.get().getPort());
    }

    public Request apply(RequestTemplate input) {
        val node = client.getNode();
        if(!node.isPresent()) {
            throw new IllegalArgumentException("No service nodes found");
        }
        val url = String.format("%s://%s:%d", secured ? "https" : "http", node.get().getHost(), node.get().getPort());
        input.insert(0, url);
        return input.request();
    }
}
