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
// https://cloud.google.com/channel/docs/codelabs/workspace/provisioning
// [END_EXCLUDE]

// [START credentials]

package channel;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.channel.v1.AdminUser;
import com.google.cloud.channel.v1.CheckCloudIdentityAccountsExistRequest;
import com.google.cloud.channel.v1.CheckCloudIdentityAccountsExistResponse;
import com.google.cloud.channel.v1.CloudChannelServiceClient;
import com.google.cloud.channel.v1.CloudChannelServiceSettings;
import com.google.cloud.channel.v1.CloudIdentityInfo;
import com.google.cloud.channel.v1.CommitmentSettings;
import com.google.cloud.channel.v1.CreateCustomerRequest;
import com.google.cloud.channel.v1.CreateEntitlementRequest;
import com.google.cloud.channel.v1.Customer;
import com.google.cloud.channel.v1.Entitlement;
import com.google.cloud.channel.v1.ListOffersRequest;
import com.google.cloud.channel.v1.Offer;
import com.google.cloud.channel.v1.OperationMetadata;
import com.google.cloud.channel.v1.Parameter;
import com.google.cloud.channel.v1.PaymentPlan;
import com.google.cloud.channel.v1.Period;
import com.google.cloud.channel.v1.PeriodType;
import com.google.cloud.channel.v1.ProvisionCloudIdentityRequest;
import com.google.cloud.channel.v1.RenewalSettings;
import com.google.cloud.channel.v1.Value;
import com.google.gson.Gson;
import com.google.type.PostalAddress;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;

// [START_EXCLUDE]

/**
 * This is a basic example of provisioning a Google Workspace customer.
 */
// [END_EXCLUDE]

public class WorkspaceProvisioner {

  //TODO: replace this values
  /***************** REPLACE WITH YOUR OWN VALUES ********************************/
  public static final String JSON_KEY_FILE = "path/to/json_key_file.json";
  public static final String RESELLER_ADMIN_USER = "admin@yourresellerdomain.com";
  public static final String ACCOUNT_ID = "C012345";
  public static final String CUSTOMER_DOMAIN = "example.com";
  /*******************************************************************************/

  public static final String ACCOUNT_NAME = "accounts/" + ACCOUNT_ID;
  private static final Gson gson = new Gson();
  private static CloudChannelServiceClient client;

  public static void main(String[] args)
      throws Exception, IOException, ExecutionException, InterruptedException {

    // Set up credentials with user impersonation
    InputStream jsonKeyFileStream = java.nio.file.Files.newInputStream(Path.of(JSON_KEY_FILE));
    GoogleCredentials credentials = ServiceAccountCredentials.fromStream(jsonKeyFileStream)
        .createScoped("https://www.googleapis.com/auth/apps.order")
        .createDelegated(RESELLER_ADMIN_USER);
    // Create the API client
    CloudChannelServiceSettings serviceSettings =
        CloudChannelServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();
    client = CloudChannelServiceClient.create(serviceSettings);
    // [END credentials]

    Offer selectedOffer = selectOffer();

    checkExists();

    Customer customer = createCustomer();

    /*Entitlement entitlement = */
    createEntitlement(customer, selectedOffer);

    // [START getAdminSDKCustomerId]
    String adminSdkCustomerId = customer.getCloudIdentityId();
    System.out.println(adminSdkCustomerId);
    // [END getAdminSDKCustomerId]
  }

  protected static Offer selectOffer() {
    // [START selectOffer]
    ListOffersRequest request =
        ListOffersRequest.newBuilder().setParent(ACCOUNT_NAME).build();

    // For the purpose of this codelab, the code lists all offers and selects
    // the first offer for Google Workspace Business Standard on an Annual
    // plan. This is needed because offerIds vary from one account to another,
    // but this is not a recommended model for your production integration
    String sampleSkuName = "Google Workspace Business Standard";
    String samplePlan = "COMMITMENT";

    CloudChannelServiceClient.ListOffersPagedResponse response = client.listOffers(request);
    Offer selectedOffer = Offer.newBuilder().build();
    Iterator<Offer> iterator = response.iterateAll().iterator();
    while (iterator.hasNext()) {
      Offer offer = iterator.next();
      String skuName = offer.getSku().getMarketingInfo().getDisplayName();
      String offerPlan = offer.getPlan().getPaymentPlan().name();
      if (sampleSkuName.equals(skuName) && samplePlan.equals(offerPlan)) {
        selectedOffer = offer;
        break;
      }
    }

    System.out.println("=== Selected offer");
    System.out.println(gson.toJson(selectedOffer));
    // [END selectOffer]

    return selectedOffer;
  }

  protected static void checkExists() throws Exception {
    // [START checkExists]
    // Determine if customer already has a cloud identity
    CheckCloudIdentityAccountsExistRequest request =
        CheckCloudIdentityAccountsExistRequest.newBuilder()
            .setParent(ACCOUNT_NAME)
            .setDomain(CUSTOMER_DOMAIN)
            .build();
    CheckCloudIdentityAccountsExistResponse response =
        client.checkCloudIdentityAccountsExist(request);

    if (response.getCloudIdentityAccountsCount() > 0) {
      throw new Exception(
          "Cloud identity already exists. "
              + "Customer must be transferred. "
              + "Out of scope for this codelab");
    }
    // [END checkExists]
  }

  protected static Customer createCustomer() throws InterruptedException, ExecutionException {
    // [START createCustomer]
    // Create the Customer resource
    PostalAddress postalAddress =
        PostalAddress.newBuilder()
            .addAddressLines("1800 Amphibious Blvd")
            .setPostalCode("94045")
            .setRegionCode("US")
            .build();

    CreateCustomerRequest request =
        CreateCustomerRequest.newBuilder()
            .setParent(ACCOUNT_NAME)
            .setCustomer(
                Customer.newBuilder()
                    .setOrgDisplayName("Acme Corp")
                    .setOrgPostalAddress(postalAddress)
                    .setDomain(CUSTOMER_DOMAIN)
                    // Distributors need to pass the following field
                    // .setChannelPartnerId(channelPartnerLinkId)
                    .build())
            .build();

    Customer customer = client.createCustomer(request);

    System.out.printf("=== Created customer with id %s%n", customer.getName());
    System.out.println(gson.toJson(customer));
    // [END createCustomer]

    // [START provisionCloudIdentity]
    CloudIdentityInfo cloudIdentityInfo =
        CloudIdentityInfo.newBuilder()
            .setAlternateEmail("john.doe@gmail.com")
            .setLanguageCode("en-US")
            .build();

    AdminUser adminUser =
        AdminUser.newBuilder()
            .setGivenName("John")
            .setFamilyName("Doe")
            .setEmail("admin@" + CUSTOMER_DOMAIN)
            .build();

    ProvisionCloudIdentityRequest cloudIdentityRequest =
        ProvisionCloudIdentityRequest.newBuilder()
            .setCustomer(customer.getName())
            .setCloudIdentityInfo(cloudIdentityInfo)
            .setUser(adminUser)
            .build();

    // This call returns a long-running operation.
    OperationFuture<Customer, OperationMetadata> operation =
        client.provisionCloudIdentityAsync(cloudIdentityRequest);

    // Wait for the long-running operation and get the result.
    customer = operation.get();
    System.out.println("=== Provisioned cloud identity");
    // [END provisionCloudIdentity]

    return customer;
  }

  protected static Entitlement createEntitlement(Customer customer, Offer selectedOffer)
      throws InterruptedException, ExecutionException {
    // [START createEntitlement]
    RenewalSettings renewalSettings =
        RenewalSettings.newBuilder()
            // Setting renewal settings to auto renew
            .setEnableRenewal(true)
            .setPaymentPlan(PaymentPlan.COMMITMENT)
            .setPaymentCycle(
                Period.newBuilder().setPeriodType(PeriodType.YEAR).setDuration(1).build())
            .build();

    CommitmentSettings commitmentSettings =
        CommitmentSettings.newBuilder().setRenewalSettings(renewalSettings).build();

    Entitlement entitlement =
        Entitlement.newBuilder()
            .setOffer(selectedOffer.getName())
            // Setting 5 seats for this Annual offer
            .addParameters(
                Parameter.newBuilder()
                    .setName("num_units")
                    .setValue(Value.newBuilder().setInt64Value(5).build())
                    .build())
            .setCommitmentSettings(commitmentSettings)
            // A string of up to 80 characters.
            // We recommend using an internal transaction ID or
            // identifier for the customer in this field.
            .setPurchaseOrderId("A codelab test")
            .build();

    CreateEntitlementRequest request =
        CreateEntitlementRequest.newBuilder()
            .setParent(customer.getName())
            .setEntitlement(entitlement)
            .build();

    // This call returns a long-running operation.
    OperationFuture<Entitlement, OperationMetadata> operation =
        client.createEntitlementAsync(request);

    // Wait for the long-running operation and get the result.
    entitlement = operation.get();
    System.out.println("=== Created entitlement");
    System.out.println(gson.toJson(entitlement));
    // [END createEntitlement]

    return entitlement;
  }
}
