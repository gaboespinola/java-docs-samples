/*
 * Copyright 2021 Google Inc.
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

package channel;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.services.siteVerification.SiteVerification;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceGettokenRequest;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceGettokenResponse;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceResource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class DomainVerifierTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  SiteVerification mockSiteVerification;

  private DomainVerifier verifier;

  private final PrintStream originalOut = System.out;
  private ByteArrayOutputStream bout;

  @Before
  public void beforeTest() throws Exception {
    MockitoAnnotations.openMocks(this);

    Field privateField = DomainVerifier.class.getDeclaredField("verificationService");
    privateField.setAccessible(true);
    privateField.set(null, mockSiteVerification);

    bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout));
  }

  @After
  public void tearDown() {
    System.setOut(originalOut);
    bout.reset();
  }

  @Test
  public void test_fetch_verification_token() throws IOException {
    SiteVerificationWebResourceGettokenResponse mockGetTokenResponse =
        Mockito.mock(SiteVerificationWebResourceGettokenResponse.class);

    when(mockSiteVerification
            .webResource()
            .getToken(ArgumentMatchers.any(SiteVerificationWebResourceGettokenRequest.class))
            .execute())
        .thenReturn(mockGetTokenResponse);
    when(mockGetTokenResponse.getToken())
        .thenReturn("google-site-verification=site_verification_token");

    verifier.fetchSiteVerificationToken();

    String output = bout.toString();
    assertTrue(output.contains("google-site-verification=site_verification_token"));
  }

  @Test
  public void test_verify_domain() throws IOException {
    SiteVerificationWebResourceResource mockSiteVerificationWebResource =
        Mockito.mock(SiteVerificationWebResourceResource.class);
    when(mockSiteVerification
            .webResource()
            .insert(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.any(SiteVerificationWebResourceResource.class))
            .execute())
        .thenReturn(mockSiteVerificationWebResource);

    verifier.verifyDomain();

    String output = bout.toString();
    assertTrue(output.contains("=== Domain has been verified"));
  }
}
