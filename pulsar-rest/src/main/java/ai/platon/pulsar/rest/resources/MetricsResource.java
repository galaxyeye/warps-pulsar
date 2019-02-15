/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package ai.platon.pulsar.rest.resources;

import ai.platon.pulsar.persist.WebDb;
import ai.platon.pulsar.persist.WebPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.stream.Collectors;

import static ai.platon.pulsar.common.config.PulsarConstants.METRICS_HOME_URL;

@Component
@Singleton
@Path("/metrics")
public class MetricsResource {
  public static final Logger LOG = LoggerFactory.getLogger(MetricsResource.class);

  private final WebDb webDb;

  @Inject
  public MetricsResource(WebDb webDb) {
    this.webDb = webDb;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public String list(
          @QueryParam("limit") @DefaultValue("1000") int limit) {
    WebPage page = webDb.getOrNil(METRICS_HOME_URL);
    if (page.isNil() || page.getLiveLinks().isEmpty()) {
      return "[]";
    }

    return "[\n" + page.getLiveLinks().values().stream()
            .limit(limit)
            .map(Object::toString)
            .collect(Collectors.joining(",\n")) + "\n]";
  }
}