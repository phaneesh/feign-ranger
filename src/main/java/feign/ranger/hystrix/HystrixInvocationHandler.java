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

package feign.ranger.hystrix;

import com.hystrix.configurator.core.BaseCommand;
import com.netflix.hystrix.HystrixCommand;
import feign.InvocationHandlerFactory;
import feign.Target;
import feign.ranger.RangerTarget;
import lombok.ToString;
import rx.Observable;
import rx.Single;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static feign.Util.checkNotNull;

/**
 * @author phaneesh
 */
@ToString
public class HystrixInvocationHandler<T> implements InvocationHandler {

    private final RangerTarget<T> target;
    private final Map<Method, InvocationHandlerFactory.MethodHandler> dispatch;
    private final Object fallback; // Nullable

    public HystrixInvocationHandler(RangerTarget<T> target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch, Object fallback) {
        this.target = checkNotNull(target, "target");
        this.dispatch = checkNotNull(dispatch, "dispatch");
        this.fallback = fallback;
    }

    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
        BaseCommand<Object> hystrixCommand = new BaseCommand<Object>(String.format("%s.%s", target.getService(), method.getName())) {
            @Override
            protected Object run() throws Exception {
                try {
                    return HystrixInvocationHandler.this.dispatch.get(method).invoke(args);
                } catch (Exception e) {
                    throw e;
                } catch (Throwable t) {
                    throw (Error) t;
                }
            }

            @Override
            protected Object getFallback() {
                if (fallback == null) {
                    return super.getFallback();
                }
                try {
                    Object result = method.invoke(fallback, args);
                    if (isReturnsHystrixCommand(method)) {
                        return ((HystrixCommand) result).execute();
                    } else if (isReturnsObservable(method)) {
                        // Create a cold Observable
                        return ((Observable) result).toBlocking().first();
                    } else if (isReturnsSingle(method)) {
                        // Create a cold Observable as a Single
                        return ((Single) result).toObservable().toBlocking().first();
                    } else {
                        return result;
                    }
                } catch (IllegalAccessException e) {
                    // shouldn't happen as method is public due to being an interface
                    throw new AssertionError(e);
                } catch (InvocationTargetException e) {
                    // Exceptions on fallback are tossed by Hystrix
                    throw new AssertionError(e.getCause());
                }
            }
        };

        if (isReturnsHystrixCommand(method)) {
            return hystrixCommand;
        } else if (isReturnsObservable(method)) {
            // Create a cold Observable
            return hystrixCommand.toObservable();
        } else if (isReturnsSingle(method)) {
            // Create a cold Observable as a Single
            return hystrixCommand.toObservable().toSingle();
        }
        return hystrixCommand.execute();
    }

    private boolean isReturnsHystrixCommand(Method method) {
        return HystrixCommand.class.isAssignableFrom(method.getReturnType());
    }

    private boolean isReturnsObservable(Method method) {
        return Observable.class.isAssignableFrom(method.getReturnType());
    }

    private boolean isReturnsSingle(Method method) {
        return Single.class.isAssignableFrom(method.getReturnType());
    }

    public static final class Factory implements InvocationHandlerFactory {

        @Override
        public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
            return new HystrixInvocationHandler((RangerTarget)target, dispatch, null);
        }
    }

}
