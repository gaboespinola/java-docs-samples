// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// [START_EXCLUDE]
// Instructions for this codelab can be found on this page
// https://cloud.google.com/channel/docs/codelabs/workspace/domain-verification
// [END_EXCLUDE]

package channel;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.siteVerification.SiteVerification;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceGettokenRequest;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceGettokenResponse;
import com.google.api.services.siteVerification.model.SiteVerificationWebResourceResource;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;

public class DomainVerifier {

  /***************** REPLACE WITH YOUR OWN VALUES ********************************/
  public static final String JSON_KEY_FILE = "path/to/json_key_file.json";

  public static final String RESELLER_ADMIN_USER = "gespinola@goog-test.reseller.gappslabs.co";
  public static final String CUSTOMER_DOMAIN = "goog-test.gespinola.03.reseller.gappslabs.co";
  /*******************************************************************************/

  private static SiteVerification verificationService;

  public static void main(String[] args) throws Exception, InterruptedException {
    // Set up credentials with user impersonation
    // FileInputStream jsonKeyFileSteam = new FileInputStream(JSON_KEY_FILE);
    InputStream jsonKeyFileStream = java.nio.file.Files.newInputStream(Path.of(JSON_KEY_FILE));
    GoogleCredentials credentials =
        ServiceAccountCredentials.fromStream(jsonKeyFileStream)
            .createScoped("https://www.googleapis.com/auth/siteverification")
            .createDelegated(RESELLER_ADMIN_USER);

    // Create the API service
    verificationService =
        new SiteVerification.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JacksonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
            .build();

    fetchSiteVerificationToken();
    verifyDomain();
  }

  protected static void fetchSiteVerificationToken() throws IOException {
    // Fetch the token
    SiteVerificationWebResourceGettokenRequest request =
        new SiteVerificationWebResourceGettokenRequest()
            .setVerificationMethod("DNS_TXT")
            .setSite(
                new SiteVerificationWebResourceGettokenRequest.Site()
                    .setType("INET_DOMAIN")
                    .setIdentifier(CUSTOMER_DOMAIN));
    SiteVerificationWebResourceGettokenResponse response =
        verificationService.webResource().getToken(request).execute();
    String token = response.getToken();
    System.out.printf("Site Verification token: %s%n", token);
  }

  protected static void verifyDomain() throws IOException {
    // Set the customer's admin user as an owner to make sure the domain
    // verification status is instantly propagated to the Workspace account
    SiteVerificationWebResourceResource resource =
        new SiteVerificationWebResourceResource()
            .setSite(
                new SiteVerificationWebResourceResource.Site()
                    .setIdentifier(CUSTOMER_DOMAIN)
                    .setType("INET_DOMAIN"))
            .setOwners(Collections.singletonList("admin@" + CUSTOMER_DOMAIN));

    /*resource = */ verificationService.webResource().insert("DNS_TXT", resource).execute();
    System.out.println("=== Domain has been verified");
  }
}
