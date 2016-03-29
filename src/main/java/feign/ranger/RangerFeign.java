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
import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.ranger.hystrix.HystrixDelegatingContract;
import feign.ranger.hystrix.HystrixInvocationHandler;
import org.apache.curator.framework.CuratorFramework;

/**
 * @author phaneesh
 */
@SuppressWarnings("unchecked")
public class RangerFeign {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends Feign.Builder {

        private Contract contract = new Contract.Default();

        public <T> T target(final Class<T> apiType, final String environment, final String namespace, final String service,
                            final CuratorFramework curator, final boolean secured, final ObjectMapper objectMapper) throws Exception {
            super.invocationHandlerFactory((t, dispatch) -> new HystrixInvocationHandler((RangerTarget)t, dispatch, null));
            super.contract(new HystrixDelegatingContract(contract));
            return target(new RangerTarget<>(apiType, environment, namespace, service, curator, secured, objectMapper));
        }

        public <T> T target(Target<T> target, final T fallback) {
            super.invocationHandlerFactory((t, dispatch) -> new HystrixInvocationHandler((RangerTarget)t, dispatch, fallback));
            super.contract(new HystrixDelegatingContract(contract));
            return super.build().newInstance(target);
        }

        public <T> T target(Class<T> apiType, String url, T fallback) {
            return target(new Target.HardCodedTarget<T>(apiType, url), fallback);
        }

        @Override
        public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        @Override
        public Feign build() {
            super.invocationHandlerFactory(new HystrixInvocationHandler.Factory());
            super.contract(new HystrixDelegatingContract(contract));
            return super.build();
        }

        // Covariant overrides to support chaining to new fallback method.
        @Override
        public Builder logLevel(Logger.Level logLevel) {
            return (Builder) super.logLevel(logLevel);
        }

        @Override
        public Builder client(Client client) {
            return (Builder) super.client(client);
        }

        @Override
        public Builder retryer(Retryer retryer) {
            return (Builder) super.retryer(retryer);
        }

        @Override
        public Builder logger(Logger logger) {
            return (Builder) super.logger(logger);
        }

        @Override
        public Builder encoder(Encoder encoder) {
            return (Builder) super.encoder(encoder);
        }

        @Override
        public Builder decoder(Decoder decoder) {
            return (Builder) super.decoder(decoder);
        }

        @Override
        public Builder decode404() {
            return (Builder) super.decode404();
        }

        @Override
        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            return (Builder) super.errorDecoder(errorDecoder);
        }

        @Override
        public Builder options(Request.Options options) {
            return (Builder) super.options(options);
        }

        @Override
        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            return (Builder) super.requestInterceptor(requestInterceptor);
        }

        @Override
        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            return (Builder) super.requestInterceptors(requestInterceptors);
        }
    }
}
