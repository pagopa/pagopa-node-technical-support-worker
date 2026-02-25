package it.gov.pagopa.nodetsworker.repository;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CosmosClientsConfigTest {

    @Test
    void shouldNotSetPreferredRegion_whenOptionalIsEmpty() throws Exception {
        CosmosClientsConfig cfg = baseConfig(Optional.empty());

        try (MockedConstruction<CosmosClientBuilder> mocked = mockConstruction(
                CosmosClientBuilder.class,
                (builder, context) -> {
                    when(builder.endpoint(anyString())).thenReturn(builder);
                    when(builder.key(anyString())).thenReturn(builder);
                    when(builder.preferredRegions(anyList())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(mock(CosmosClient.class));
                })) {

            CosmosClient client = cfg.bizClient();
            assertNotNull(client);

            CosmosClientBuilder b = mocked.constructed().get(0);

            verify(b).endpoint("https://biz-endpoint");
            verify(b).key("biz-key");
            verify(b).buildClient();

            // preferredRegions must NOT be called
            verify(b, never()).preferredRegions(anyList());
        }
    }

    @Test
    void shouldNotSetPreferredRegion_whenOptionalIsBlank() throws Exception {
        CosmosClientsConfig cfg = baseConfig(Optional.of("   ")); // blank

        try (MockedConstruction<CosmosClientBuilder> mocked = mockConstruction(
                CosmosClientBuilder.class,
                (builder, context) -> {
                    when(builder.endpoint(anyString())).thenReturn(builder);
                    when(builder.key(anyString())).thenReturn(builder);
                    when(builder.preferredRegions(anyList())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(mock(CosmosClient.class));
                })) {

            CosmosClient client = cfg.bizClient();
            assertNotNull(client);

            CosmosClientBuilder b = mocked.constructed().get(0);

            verify(b).endpoint("https://biz-endpoint");
            verify(b).key("biz-key");
            verify(b).buildClient();

            // blank -> not applied
            verify(b, never()).preferredRegions(anyList());
        }
    }

    @Test
    void shouldSetPreferredRegion_whenOptionalIsPresent() throws Exception {
        CosmosClientsConfig cfg = baseConfig(Optional.of(" North Europe ")); // with extra spaces, should be trimmed

        try (MockedConstruction<CosmosClientBuilder> mocked = mockConstruction(
                CosmosClientBuilder.class,
                (builder, context) -> {
                    when(builder.endpoint(anyString())).thenReturn(builder);
                    when(builder.key(anyString())).thenReturn(builder);
                    when(builder.preferredRegions(anyList())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(mock(CosmosClient.class));
                })) {

            CosmosClient client = cfg.bizClient();
            assertNotNull(client);

            CosmosClientBuilder b = mocked.constructed().get(0);

            verify(b).endpoint("https://biz-endpoint");
            verify(b).key("biz-key");

            // applied with value
            verify(b).preferredRegions(List.of("North Europe"));

            verify(b).buildClient();
        }
    }

    @Test
    void producerMethods_shouldUseCorrectEndpointsAndKeys() throws Exception {
        CosmosClientsConfig cfg = baseConfig(Optional.of("North Europe"));

        try (MockedConstruction<CosmosClientBuilder> mocked = mockConstruction(
                CosmosClientBuilder.class,
                (builder, context) -> {
                    when(builder.endpoint(anyString())).thenReturn(builder);
                    when(builder.key(anyString())).thenReturn(builder);
                    when(builder.preferredRegions(anyList())).thenReturn(builder);
                    when(builder.buildClient()).thenReturn(mock(CosmosClient.class));
                })) {

            assertNotNull(cfg.bizClient());
            assertNotNull(cfg.negbizClient());
            assertNotNull(cfg.verifyKoClient());

            // 3 producers -> 3 builders
            List<CosmosClientBuilder> builders = mocked.constructed();

            CosmosClientBuilder b1 = builders.get(0);
            verify(b1).endpoint("https://biz-endpoint");
            verify(b1).key("biz-key");
            verify(b1).preferredRegions(List.of("North Europe"));

            CosmosClientBuilder b2 = builders.get(1);
            verify(b2).endpoint("https://bizneg-endpoint");
            verify(b2).key("bizneg-key");
            verify(b2).preferredRegions(List.of("North Europe"));

            CosmosClientBuilder b3 = builders.get(2);
            verify(b3).endpoint("https://verifyko-endpoint");
            verify(b3).key("verifyko-key");
            verify(b3).preferredRegions(List.of("North Europe"));
        }
    }

    // --------------------
    // helpers
    // --------------------

    private static CosmosClientsConfig baseConfig(Optional<String> preferredRegion) throws Exception {
        CosmosClientsConfig cfg = new CosmosClientsConfig();
        setField(cfg, "bizendpoint", "https://biz-endpoint");
        setField(cfg, "bizkey", "biz-key");
        setField(cfg, "biznegendpoint", "https://bizneg-endpoint");
        setField(cfg, "biznegkey", "bizneg-key");
        setField(cfg, "verifykoendpoint", "https://verifyko-endpoint");
        setField(cfg, "verifykokey", "verifyko-key");
        setField(cfg, "preferredRegion", preferredRegion);
        return cfg;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}