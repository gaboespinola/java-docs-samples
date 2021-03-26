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
// https://cloud.google.com/channel/docs/codelabs/gcp/provisioning
// [END_EXCLUDE]

// [START credentials]

package channel;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.billing.v1.CloudBillingClient;
import com.google.cloud.billing.v1.CloudBillingSettings;
import com.google.cloud.channel.v1.CloudChannelServiceClient;
import com.google.cloud.channel.v1.CloudChannelServiceSettings;
import com.google.cloud.channel.v1.CreateCustomerRequest;
import com.google.cloud.channel.v1.CreateEntitlementRequest;
import com.google.cloud.channel.v1.Customer;
import com.google.cloud.channel.v1.Entitlement;
import com.google.cloud.channel.v1.ListOffersRequest;
import com.google.cloud.channel.v1.Offer;
import com.google.cloud.channel.v1.OperationMetadata;
import com.google.cloud.channel.v1.Parameter;
import com.google.cloud.channel.v1.Value;
import com.google.gson.Gson;
import com.google.iam.v1.Binding;
import com.google.iam.v1.Policy;
import com.google.type.PostalAddress;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

// [START_EXCLUDE]

/** This is a basic example of provisioning a GCP customer. */
// [END_EXCLUDE]

public class GcpProvisioner {

  /***************** REPLACE WITH YOUR OWN VALUES ********************************/
  /*public static final String JSON_KEY_FILE =
      "/usr/local/google/home/gespinola/workspaces/gespinola-project-01-key.json";

  public static final String RESELLER_ADMIN_USER = "gespinola@goog-test.reseller.gappslabs.co";
  public static final String ACCOUNT_ID = "C02a7qnez";
  public static final String CUSTOMER_DOMAIN = "goog-test.gespinola.10.reseller.gappslabs.co";*/
  /*******************************************************************************/
  /***************** REPLACE WITH YOUR OWN VALUES ********************************/
  public static final String JSON_KEY_FILE = "path/to/json_key_file.json";
  public static final String RESELLER_ADMIN_USER = "admin@yourresellerdomain.com";
  public static final String ACCOUNT_ID = "C012345";
  public static final String CUSTOMER_DOMAIN = "example.com";
  /*******************************************************************************/

  public static final String ACCOUNT_NAME = "accounts/" + ACCOUNT_ID;

  private static CloudChannelServiceClient client;
  private static final Gson gson = new Gson();

  public static void main(String[] args)
      throws IOException, ExecutionException, InterruptedException {

    // Set up credentials with user impersonation
    // FileInputStream jsonKeyFileSteam = new FileInputStream(JSON_KEY_FILE);
    InputStream jsonKeyFileStream = java.nio.file.Files.newInputStream(Path.of(JSON_KEY_FILE));
    GoogleCredentials credentials =
        ServiceAccountCredentials.fromStream(jsonKeyFileStream)
            .createScoped("https://www.googleapis.com/auth/apps.order")
            .createDelegated(RESELLER_ADMIN_USER);

    CloudChannelServiceSettings clientSettings =
        CloudChannelServiceSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();

    // Create the API client
    client = CloudChannelServiceClient.create(clientSettings);
    // [END credentials]

    Offer selectedOffer = selectOffer();

    Customer customer = createCustomer();

    Entitlement entitlement = createEntitlement(customer, selectedOffer);

    setIamPolicy(entitlement);
  }

  protected static Offer selectOffer() {
    // [START selectOffer]
    ListOffersRequest request = ListOffersRequest.newBuilder().setParent(ACCOUNT_NAME).build();

    // For the purpose of this codelab, the code lists all offers and selects
    // the first offer for Google Cloud Platform.
    // This is needed because offerIds vary from one account to another,
    // but this is not a recommended model for your production integration
    CloudChannelServiceClient.ListOffersPagedResponse response = client.listOffers(request);
    Offer selectedOffer = Offer.newBuilder().build();
    Iterator<Offer> iterator = response.iterateAll().iterator();
    while (iterator.hasNext()) {
      Offer offer = iterator.next();
      String skuName = offer.getSku().getMarketingInfo().getDisplayName();
      if ("Google Cloud Platform".equals(skuName)) {
        selectedOffer = offer;
        break;
      }
    }

    System.out.println("=== Selected offer");
    System.out.println(gson.toJson(selectedOffer));
    // [END selectOffer]

    return selectedOffer;
  }

  protected static Customer createCustomer() {
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

    return customer;
  }

  protected static Entitlement createEntitlement(Customer customer, Offer selectedOffer)
      throws InterruptedException, ExecutionException {
    // [START createEntitlement]
    // This display name shows on the Google Cloud console when a customer
    // links the account to their project.
    // Recommended format: "[Reseller name] - [Customer name]"
    String displayName = "Reseller XYZ - Acme corp";

    Entitlement entitlement =
        Entitlement.newBuilder()
            .setOffer(selectedOffer.getName())
            .addParameters(
                Parameter.newBuilder()
                    .setName("display_name")
                    .setValue(Value.newBuilder().setStringValue(displayName).build())
                    .build())
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

  protected static void setIamPolicy(Entitlement entitlement) throws IOException {
    // [START setIamPolicy]
    // Get the name of the customer's billing account from the entitlement
    String billingAccount = entitlement.getProvisionedService().getProvisioningId();

    // Create a Cloud Billing API client
    // FileInputStream jsonKeyFileSteam = new FileInputStream(JSON_KEY_FILE);
    InputStream jsonKeyFileStream = java.nio.file.Files.newInputStream(Path.of(JSON_KEY_FILE));
    GoogleCredentials credentials = ServiceAccountCredentials.fromStream(jsonKeyFileStream);
    CloudBillingSettings clientSettings =
        CloudBillingSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build();
    CloudBillingClient client = CloudBillingClient.create(clientSettings);

    // For the purpose of this codelab, we'll grant an IAM role to the reseller
    // admin user, but this is not a requirement for a production integration.
    Policy policy = client.getIamPolicy(billingAccount);

    Binding binding = null;
    int bindingIndex = 0;
    String role = "roles/billing.user";
    String member = "user:" + RESELLER_ADMIN_USER;
    // getBindingsList() returns an ImmutableList and copying over to an ArrayList so it's mutable.
    List<Binding> bindings = new ArrayList(policy.getBindingsList());
    for (int index = 0; index < bindings.size(); index++) {
      Binding b = bindings.get(index);
      if (role.equals(b.getRole())) {
        binding = b;
        bindingIndex = index;
        break;
      }
    }

    // If the binding already exists, add the user to it, else add a new binding
    Policy.Builder newPolicyBuilder = policy.toBuilder();
    if (binding != null) {
      newPolicyBuilder.setBindings(bindingIndex, binding.toBuilder().addMembers(member).build());
    } else {
      binding = Binding.newBuilder().setRole(role).addMembers(member).build();
      newPolicyBuilder.addBindings(binding);
    }

    // Update the IAM policy
    Policy newPolicy = newPolicyBuilder.build();
    client.setIamPolicy(billingAccount, newPolicy);
    System.out.println("=== Set IAM policy");
    System.out.println(gson.toJson(newPolicy));
    // [END setIamPolicy]
  }

}
