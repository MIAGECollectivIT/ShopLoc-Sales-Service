package fr.miage.collectivit.salesservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("StoreClient Unit Tests")
class StoreClientTest {

  private StoreClient storeClient;
  private MockRestServiceServer mockServer;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8089");
    mockServer = MockRestServiceServer.bindTo(builder).build();
    storeClient = new StoreClient(null, "http://localhost:8089");
    storeClient.setRestClient(builder.build());
  }

  @Test
  @DisplayName("getMerchantStoreUids should read 'uid' field strictly and ignore numeric 'id'")
  void shouldReadOnlyUidFromStoresMe() {
    UUID storeUid1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID storeUid2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    mockServer
        .expect(requestTo("http://localhost:8089/v1/stores/me"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                """
                [
                  {"uid": "%s", "name": "Store 1"},
                  {"id": 99, "name": "Legacy without uid"},
                  {"uid": "%s", "id": 100, "name": "Store 2"}
                ]
                """
                    .formatted(storeUid1, storeUid2),
                MediaType.APPLICATION_JSON));

    Set<UUID> result = storeClient.getMerchantStoreUids("jwt-token");

    assertThat(result).containsExactlyInAnyOrder(storeUid1, storeUid2);
    mockServer.verify();
  }

  @Test
  @DisplayName("getProductPrice and updateStock should call UUID endpoints")
  void shouldCallUuidEndpointsForProductPriceAndStock() {
    UUID storeUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID productUid = UUID.fromString("33333333-3333-3333-3333-333333333333");

    mockServer
        .expect(
            requestTo("http://localhost:8089/v1/stores/" + storeUid + "/products/" + productUid))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("{\"price\": 4.50}", MediaType.APPLICATION_JSON));

    mockServer
        .expect(
            requestTo(
                "http://localhost:8089/v1/stores/"
                    + storeUid
                    + "/products/"
                    + productUid
                    + "/stock"))
        .andExpect(method(HttpMethod.PATCH))
        .andRespond(withSuccess());

    Optional<BigDecimal> price = storeClient.getProductPrice(storeUid, productUid);
    assertThat(price).contains(new BigDecimal("4.50"));

    storeClient.updateStock(storeUid, productUid, -2);
    mockServer.verify();
  }
}
