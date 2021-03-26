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

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.when;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.channel.v1.CheckCloudIdentityAccountsExistRequest;
import com.google.cloud.channel.v1.CheckCloudIdentityAccountsExistResponse;
import com.google.cloud.channel.v1.CloudChannelServiceClient;
import com.google.cloud.channel.v1.CloudChannelServiceClient.ListOffersPagedResponse;
import com.google.cloud.channel.v1.CreateCustomerRequest;
import com.google.cloud.channel.v1.CreateEntitlementRequest;
import com.google.cloud.channel.v1.Customer;
import com.google.cloud.channel.v1.Entitlement;
import com.google.cloud.channel.v1.ListOffersRequest;
import com.google.cloud.channel.v1.Offer;
import com.google.cloud.channel.v1.OperationMetadata;
import com.google.cloud.channel.v1.ProvisionCloudIdentityRequest;
import java.lang.reflect.Field;
import java.util.Iterator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@RunWith(JUnit4.class)
public class WorkspaceProvisionerTest {

  @Mock
  CloudChannelServiceClient mockClient;
  private WorkspaceProvisioner provisioner;

  @Before
  public void beforeTest() throws Exception {
    MockitoAnnotations.openMocks(this);

    Field privateField = WorkspaceProvisioner.class.getDeclaredField("client");
    privateField.setAccessible(true);
    privateField.set(null, mockClient);

  }

  @Test
  public void list_all_offers() {
    ListOffersPagedResponse mockListOffersPagedResponse = Mockito
        .mock(ListOffersPagedResponse.class, Mockito.RETURNS_DEEP_STUBS);
    Iterator<Offer> mockIterator = Mockito.mock(Iterator.class);
    Offer mockOffer = Mockito.mock(Offer.class, Mockito.RETURNS_DEEP_STUBS);

    when(mockClient.listOffers(ArgumentMatchers.isA(ListOffersRequest.class)))
        .thenReturn(mockListOffersPagedResponse);
    when(mockListOffersPagedResponse.iterateAll().iterator()).thenReturn(mockIterator);
    when(mockIterator.hasNext()).thenReturn(true, false);
    when(mockIterator.next()).thenReturn(mockOffer);
    when(mockOffer.getSku()
        .getMarketingInfo()
        .getDisplayName())
        .thenReturn("Google Workspace Business Standard");
    when(mockOffer.getPlan().getPaymentPlan().name()).thenReturn("COMMITMENT");

    Offer selectedOffer = provisioner.selectOffer();
    assertSame(mockOffer, selectedOffer);
  }

  @Test(expected = Exception.class)
  public void check_existing_cloud_identity() throws Exception {
    CheckCloudIdentityAccountsExistResponse mockResponse = Mockito
        .mock(CheckCloudIdentityAccountsExistResponse.class);

    when(mockResponse.getCloudIdentityAccountsCount()).thenReturn(1);
    when(mockClient.checkCloudIdentityAccountsExist(ArgumentMatchers.isA(
        CheckCloudIdentityAccountsExistRequest.class))).thenReturn(mockResponse);

    provisioner.checkExists();
  }

  @Test
  public void create_customer() throws Exception {
    Customer mockCustomer = Mockito.mock(Customer.class);
    OperationFuture mockOperationFuture = Mockito.mock(OperationFuture.class);
    Customer mockCustomerWithCloudIdentity = Mockito.mock(Customer.class);

    when(mockClient.createCustomer(ArgumentMatchers.isA(CreateCustomerRequest.class)))
        .thenReturn(mockCustomer);
    when(mockCustomer.getName()).thenReturn("accounts/C012345/customers/Snh6kUpcdvT2vm");
    when(mockClient
        .provisionCloudIdentityAsync(ArgumentMatchers.isA(ProvisionCloudIdentityRequest.class)))
        .thenReturn(mockOperationFuture);
    when(mockOperationFuture.get()).thenReturn(mockCustomerWithCloudIdentity);

    Customer customer = provisioner.createCustomer();
    assertNotSame(customer, mockCustomer);

  }

  @Test
  public void create_entitlement() throws Exception {
    Offer mockOffer = Mockito.mock(Offer.class, Mockito.RETURNS_DEEP_STUBS);
    Customer mockCustomer = Mockito.mock(Customer.class);
    OperationFuture<Entitlement, OperationMetadata> mockOperationFuture = Mockito
        .mock(OperationFuture.class);
    Entitlement mockEntitlement = Mockito.mock(Entitlement.class);

    when(mockClient.createEntitlementAsync(ArgumentMatchers.isA(CreateEntitlementRequest.class)))
        .thenReturn(mockOperationFuture);
    when(mockOperationFuture.get()).thenReturn(mockEntitlement);
    when(mockEntitlement.getName())
        .thenReturn("accounts/C012345/customers/Snh6kUpcdvT2vm/entitlements/Sc7dSgCRqffeYc");
    when(mockOffer.getName()).thenReturn("accounts/C012345/offers/S240y00K5UnCo9");
    when(mockCustomer.getName()).thenReturn("Acme Corp");

    Entitlement entitlement = provisioner.createEntitlement(mockCustomer, mockOffer);
    assertSame(mockEntitlement, entitlement);

  }


}
