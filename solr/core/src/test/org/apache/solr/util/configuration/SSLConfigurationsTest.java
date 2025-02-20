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

package org.apache.solr.util.configuration;

import static org.hamcrest.CoreMatchers.is;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.TestRuleRestoreSystemProperties;
import org.apache.solr.SolrTestCase;
import org.apache.solr.util.configuration.providers.EnvSSLCredentialProvider;
import org.apache.solr.util.configuration.providers.SysPropSSLCredentialProvider;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

public class SSLConfigurationsTest extends SolrTestCase {
  private Map<String, String> envs;
  private SSLConfigurations sut;

  public static final String SAMPLE_PW1 = "pw123";
  public static final String SAMPLE_PW2 = "pw456";
  public static final String SAMPLE_PW3 = "pw789";
  public static final String KEY_STORE_PASSWORD = SSLConfigurations.SysProps.SSL_KEY_STORE_PASSWORD;
  public static final String TRUST_STORE_PASSWORD =
      SSLConfigurations.SysProps.SSL_TRUST_STORE_PASSWORD;
  public static final String CLIENT_KEY_STORE_PASSWORD =
      SSLConfigurations.SysProps.SSL_CLIENT_KEY_STORE_PASSWORD;
  public static final String CLIENT_TRUST_STORE_PASSWORD =
      SSLConfigurations.SysProps.SSL_CLIENT_TRUST_STORE_PASSWORD;

  @Rule
  public TestRule syspropRestore =
      new TestRuleRestoreSystemProperties(
          SSLConfigurations.SysProps.SSL_KEY_STORE_PASSWORD,
          SSLConfigurations.SysProps.SSL_TRUST_STORE_PASSWORD,
          SSLConfigurations.SysProps.SSL_CLIENT_KEY_STORE_PASSWORD,
          SSLConfigurations.SysProps.SSL_CLIENT_TRUST_STORE_PASSWORD);

  @Before
  public void setUp() throws Exception {
    super.setUp();
    envs = new HashMap<>();
  }

  private SSLConfigurations createSut() {
    EnvSSLCredentialProvider envSSLCredentialProvider = new EnvSSLCredentialProvider();
    envSSLCredentialProvider.setEnvVars(envs);
    sut =
        new SSLConfigurations(
            Arrays.asList(envSSLCredentialProvider, new SysPropSSLCredentialProvider()));
    return sut;
  }

  @Test
  public void testSslConfigKeystorePwFromKeystoreEnvVar() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_KEY_STORE_PASSWORD, SAMPLE_PW1);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_KEY_STORE_PASSWORD), is(SAMPLE_PW1));
  }

  @Test
  public void testSslConfigKeystorePwFromClientKeystoreEnvVar() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_KEY_STORE_PASSWORD), is(SAMPLE_PW2));
  }

  @Test
  public void testSslConfigKeystorePwFromBothEnvVars() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_KEY_STORE_PASSWORD, SAMPLE_PW1);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_KEY_STORE_PASSWORD), is(SAMPLE_PW2));
  }

  @Test
  public void testSslConfigKeystorePwNotOverwrittenIfExists() {
    System.setProperty(CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW3);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_KEY_STORE_PASSWORD, SAMPLE_PW1);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(
        System.getProperty(CLIENT_KEY_STORE_PASSWORD), is(SAMPLE_PW3)); // unchanged
  }

  @Test
  public void testSslConfigTruststorePwFromKeystoreEnvVar() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_TRUST_STORE_PASSWORD, SAMPLE_PW1);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_TRUST_STORE_PASSWORD), is(SAMPLE_PW1));
  }

  @Test
  public void testSslConfigTruststorePwFromClientKeystoreEnvVar() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_TRUST_STORE_PASSWORD), is(SAMPLE_PW2));
  }

  @Test
  public void testSslConfigTruststorePwFromBothEnvVars() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_TRUST_STORE_PASSWORD, SAMPLE_PW1);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(System.getProperty(CLIENT_TRUST_STORE_PASSWORD), is(SAMPLE_PW2));
  }

  @Test
  public void testSslConfigTruststorePwNotOverwrittenIfExists() {
    System.setProperty(CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW3);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_TRUST_STORE_PASSWORD, SAMPLE_PW1);
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW2);
    createSut().init();
    MatcherAssert.assertThat(
        System.getProperty(CLIENT_TRUST_STORE_PASSWORD), is(SAMPLE_PW3)); // unchanged
  }

  @Test
  public void testGetKeyStorePasswordFromProperty() {
    System.setProperty(KEY_STORE_PASSWORD, SAMPLE_PW1);
    MatcherAssert.assertThat(createSut().getKeyStorePassword(), is(SAMPLE_PW1));
  }

  @Test
  public void testGetKeyStorePasswordFromEnv() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_KEY_STORE_PASSWORD, SAMPLE_PW2);
    MatcherAssert.assertThat(createSut().getKeyStorePassword(), is(SAMPLE_PW2));
  }

  @Test
  public void testGetTrustStorePasswordFromProperty() {
    System.setProperty(TRUST_STORE_PASSWORD, SAMPLE_PW1);
    MatcherAssert.assertThat(createSut().getTrustStorePassword(), is(SAMPLE_PW1));
  }

  @Test
  public void testGetTrustStorePasswordFromEnv() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_TRUST_STORE_PASSWORD, SAMPLE_PW2);
    MatcherAssert.assertThat(createSut().getTrustStorePassword(), is(SAMPLE_PW2));
  }

  @Test
  public void testGetClientKeyStorePasswordFromProperty() {
    System.setProperty(CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW1);
    MatcherAssert.assertThat(createSut().getClientKeyStorePassword(), is(SAMPLE_PW1));
  }

  @Test
  public void testGetClientKeyStorePasswordFromEnv() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_KEY_STORE_PASSWORD, SAMPLE_PW2);
    MatcherAssert.assertThat(createSut().getClientKeyStorePassword(), is(SAMPLE_PW2));
  }

  @Test
  public void testGetClientTrustStorePasswordFromProperty() {
    System.setProperty(CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW1);
    MatcherAssert.assertThat(createSut().getClientTrustStorePassword(), is(SAMPLE_PW1));
  }

  @Test
  public void testGetClientTrustStorePasswordFromEnv() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_CLIENT_TRUST_STORE_PASSWORD, SAMPLE_PW2);
    MatcherAssert.assertThat(createSut().getClientTrustStorePassword(), is(SAMPLE_PW2));
  }

  @Test
  public void testSystemPropertyPriorityOverEnvVar() {
    envs.put(EnvSSLCredentialProvider.EnvVars.SOLR_SSL_KEY_STORE_PASSWORD, SAMPLE_PW2);
    MatcherAssert.assertThat(createSut().getKeyStorePassword(), is(SAMPLE_PW2));
    System.setProperty(KEY_STORE_PASSWORD, SAMPLE_PW1);
    MatcherAssert.assertThat(createSut().getKeyStorePassword(), is(SAMPLE_PW2));
  }
}
