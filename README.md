# Feign Ranger [![Travis build status](https://travis-ci.org/phaneesh/feign-ranger.svg?branch=master)](https://travis-ci.org/phaneesh/feign-ranger)

This is a [feign] (https://github.com/Netflix/feign) extension that supports using [ranger](https://github.com/flipkart-incubator/ranger) discovered nodes as target.
Also, the http calls are wrapped inside a [Hystrix](https://github.com/Netflix/Hystrix) command which allows granular control of execution environment. 
This library compiles only on Java 8.
 
## Dependencies
* ranger 0.2.1  
* feign 8.15.1
* jackson 2.6.3

## Usage
Feign Ranger extension provides a easy way to enable providing service node discovery environments. Using service discovery with feign 
brings the ease of use of feign in a dynamic environment where providing target host port is counter productive or not possible (when applications are running in mesos/dcos). 
 
### Build instructions
  - Clone the source:
        git clone github.com/phaneesh/feign-ranger

  - Build

        mvn install

### Maven Dependency
Use the following repository:
```xml
<repository>
    <id>clojars</id>
    <name>Clojars repository</name>
    <url>https://clojars.org/repo</url>
</repository>
```
Use the following maven dependency:
```xml
<dependency>
    <groupId>feign.ranger</groupId>
    <artifactId>feign-ranger</artifactId>
    <version>0.0.1</version>
</dependency>
```

### Using Feign Ranger Extension
```java
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
static class TestResponse {

    private String message;
}

interface TestApi {
    @RequestLine("GET /v1/test")
    HystrixCommand<TestResponse> test();
}

TestApi api = RangerFeign.builder()
                .decoder(new JacksonDecoder())
                .encoder(new JacksonEncoder())
                .target(TestApi.class, "test", "test", "test", "test", curator, false, objectMapper);
api.test().queue().get();
                
```


LICENSE
-------

Copyright 2016 Phaneesh Nagaraja <phaneesh.n@gmail.com>.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.