/*
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
 */
package org.apache.solr.handler.admin;

import static org.apache.solr.common.params.CommonParams.NAME;
import static org.apache.solr.handler.configsets.UploadConfigSetFileAPI.FILEPATH_PLACEHOLDER;

import com.google.common.collect.Maps;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.api.AnnotatedApi;
import org.apache.solr.api.Api;
import org.apache.solr.api.JerseyResource;
import org.apache.solr.api.PayloadObj;
import org.apache.solr.client.solrj.request.beans.CreateConfigPayload;
import org.apache.solr.cloud.ConfigSetCmds;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.ConfigSetParams;
import org.apache.solr.common.params.ConfigSetParams.ConfigSetAction;
import org.apache.solr.common.params.DefaultSolrParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.handler.api.V2ApiUtils;
import org.apache.solr.handler.configsets.CreateConfigSetAPI;
import org.apache.solr.handler.configsets.DeleteConfigSetAPI;
import org.apache.solr.handler.configsets.ListConfigSetsAPI;
import org.apache.solr.handler.configsets.UploadConfigSetAPI;
import org.apache.solr.handler.configsets.UploadConfigSetFileAPI;
import org.apache.solr.request.DelegatingSolrQueryRequest;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A {@link org.apache.solr.request.SolrRequestHandler} for ConfigSets API requests. */
public class ConfigSetsHandler extends RequestHandlerBase implements PermissionNameProvider {
  // TODO refactor into o.a.s.handler.configsets package to live alongside actual API logic
  public static final Boolean DISABLE_CREATE_AUTH_CHECKS =
      Boolean.getBoolean("solr.disableConfigSetsCreateAuthChecks"); // this is for back compat only
  public static final String DEFAULT_CONFIGSET_NAME = "_default";
  public static final String AUTOCREATED_CONFIGSET_SUFFIX = ".AUTOCREATED";
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  protected final CoreContainer coreContainer;
  public static long CONFIG_SET_TIMEOUT = 300 * 1000;
  /**
   * Overloaded ctor to inject CoreContainer into the handler.
   *
   * @param coreContainer Core Container of the solr webapp installed.
   */
  public ConfigSetsHandler(final CoreContainer coreContainer) {
    this.coreContainer = coreContainer;
  }

  public static String getSuffixedNameForAutoGeneratedConfigSet(String configName) {
    return configName + AUTOCREATED_CONFIGSET_SUFFIX;
  }

  public static boolean isAutoGeneratedConfigSet(String configName) {
    return configName != null && configName.endsWith(AUTOCREATED_CONFIGSET_SUFFIX);
  }

  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    checkErrors();

    // Pick the action
    final SolrParams requiredSolrParams = req.getParams().required();
    final String actionStr = requiredSolrParams.get(ConfigSetParams.ACTION);
    ConfigSetAction action = ConfigSetAction.get(actionStr);
    if (action == null) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "Unknown action: " + actionStr);
    }

    switch (action) {
      case DELETE:
        final DeleteConfigSetAPI deleteConfigSetAPI = new DeleteConfigSetAPI(coreContainer);
        final SolrQueryRequest v2DeleteReq =
            new DelegatingSolrQueryRequest(req) {
              @Override
              public Map<String, String> getPathTemplateValues() {
                return Map.of(
                    DeleteConfigSetAPI.CONFIGSET_NAME_PLACEHOLDER,
                    req.getParams().required().get(NAME));
              }
            };
        deleteConfigSetAPI.deleteConfigSet(v2DeleteReq, rsp);
        break;
      case UPLOAD:
        final SolrQueryRequest v2UploadReq =
            new DelegatingSolrQueryRequest(req) {
              @Override
              public Map<String, String> getPathTemplateValues() {
                final Map<String, String> templateValsByName = Maps.newHashMap();

                templateValsByName.put(
                    UploadConfigSetAPI.CONFIGSET_NAME_PLACEHOLDER,
                    req.getParams().required().get(NAME));
                if (!req.getParams().get(ConfigSetParams.FILE_PATH, "").isEmpty()) {
                  templateValsByName.put(
                      FILEPATH_PLACEHOLDER, req.getParams().get(ConfigSetParams.FILE_PATH));
                }
                return templateValsByName;
              }

              // Set the v1 default vals where they differ from v2's
              @Override
              public SolrParams getParams() {
                final ModifiableSolrParams v1Defaults = new ModifiableSolrParams();
                v1Defaults.add(ConfigSetParams.OVERWRITE, "false");
                v1Defaults.add(ConfigSetParams.CLEANUP, "false");
                return new DefaultSolrParams(super.getParams(), v1Defaults);
              }
            };
        if (req.getParams()
            .get(ConfigSetParams.FILE_PATH, "")
            .isEmpty()) { // Uploading a whole configset
          new UploadConfigSetAPI(coreContainer).uploadConfigSet(v2UploadReq, rsp);
        } else { // Uploading a single file
          new UploadConfigSetFileAPI(coreContainer).updateConfigSetFile(v2UploadReq, rsp);
        }
        break;
      case LIST:
        final ListConfigSetsAPI listConfigSetsAPI = new ListConfigSetsAPI(coreContainer);
        V2ApiUtils.squashIntoSolrResponseWithoutHeader(rsp, listConfigSetsAPI.listConfigSet());
        break;
      case CREATE:
        final String newConfigSetName = req.getParams().get(NAME);
        if (newConfigSetName == null || newConfigSetName.length() == 0) {
          throw new SolrException(ErrorCode.BAD_REQUEST, "ConfigSet name not specified");
        }

        // Map v1 parameters into v2 format and process request
        final CreateConfigPayload createPayload = new CreateConfigPayload();
        createPayload.name = newConfigSetName;
        if (req.getParams().get(ConfigSetCmds.BASE_CONFIGSET) != null) {
          createPayload.baseConfigSet = req.getParams().get(ConfigSetCmds.BASE_CONFIGSET);
        }
        createPayload.properties = new HashMap<>();
        req.getParams().stream()
            .filter(entry -> entry.getKey().startsWith(ConfigSetCmds.CONFIG_SET_PROPERTY_PREFIX))
            .forEach(
                entry -> {
                  final String newKey =
                      entry.getKey().substring(ConfigSetCmds.CONFIG_SET_PROPERTY_PREFIX.length());
                  final Object value =
                      (entry.getValue().length == 1) ? entry.getValue()[0] : entry.getValue();
                  createPayload.properties.put(newKey, value);
                });
        final CreateConfigSetAPI createConfigSetAPI = new CreateConfigSetAPI(coreContainer);
        createConfigSetAPI.create(new PayloadObj<>("create", null, createPayload, req, rsp));
        break;
      default:
        throw new IllegalStateException("Unexpected ConfigSetAction detected: " + action);
    }
    rsp.setHttpCaching(false);
  }

  protected void checkErrors() {
    if (coreContainer == null) {
      throw new SolrException(ErrorCode.BAD_REQUEST, "Core container instance missing");
    }

    // Make sure that the core is ZKAware
    if (!coreContainer.isZooKeeperAware()) {
      throw new SolrException(
          ErrorCode.BAD_REQUEST, "Solr instance is not running in SolrCloud mode.");
    }
  }

  @Override
  public String getDescription() {
    return "Manage SolrCloud ConfigSets";
  }

  @Override
  public Category getCategory() {
    return Category.ADMIN;
  }

  public Boolean registerV2() {
    return true;
  }

  @Override
  public Collection<Api> getApis() {
    final List<Api> apis = new ArrayList<>();
    apis.addAll(AnnotatedApi.getApis(new CreateConfigSetAPI(coreContainer)));
    apis.addAll(AnnotatedApi.getApis(new DeleteConfigSetAPI(coreContainer)));
    apis.addAll(AnnotatedApi.getApis(new UploadConfigSetAPI(coreContainer)));
    apis.addAll(AnnotatedApi.getApis(new UploadConfigSetFileAPI(coreContainer)));

    return apis;
  }

  @Override
  public Collection<Class<? extends JerseyResource>> getJerseyResources() {
    return List.of(ListConfigSetsAPI.class);
  }

  @Override
  public Name getPermissionName(AuthorizationContext ctx) {
    String a = ctx.getParams().get(ConfigSetParams.ACTION);
    if (a != null) {
      ConfigSetAction action = ConfigSetAction.get(a);
      if (action == ConfigSetAction.CREATE
          || action == ConfigSetAction.DELETE
          || action == ConfigSetAction.UPLOAD) {
        return Name.CONFIG_EDIT_PERM;
      } else if (action == ConfigSetAction.LIST) {
        return Name.CONFIG_READ_PERM;
      }
    }
    return null;
  }
}
