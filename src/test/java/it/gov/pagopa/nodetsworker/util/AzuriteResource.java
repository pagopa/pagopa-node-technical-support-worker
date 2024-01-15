//package it.gov.pagopa.nodetsworker.util;
//
//import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
//import java.util.HashMap;
//import java.util.Map;
//import lombok.SneakyThrows;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.utility.DockerImageName;
//
//public class AzuriteResource implements QuarkusTestResourceLifecycleManager {
//
//  private GenericContainer azurite;
//
//  @SneakyThrows
//  @Override
//  public Map<String, String> start() {
//    azurite =
//        new GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite"))
//            .withExposedPorts(10000, 10001, 10002);
//    azurite.start();
//
//    String connectStr =
//        "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:"
//            + azurite.getMappedPort(10000)
//            + "/devstoreaccount1;QueueEndpoint=http://127.0.0.1:"
//            + azurite.getMappedPort(10001)
//            + "/devstoreaccount1;TableEndpoint=http://127.0.0.1:"
//            + azurite.getMappedPort(10002)
//            + "/devstoreaccount1;";
//    Map<String, String> conf = new HashMap<>();
//    conf.put("mockserver.azurite.connection-string", connectStr);
//    return conf;
//  }
//
//  @Override
//  public void stop() {
//    azurite.stop();
//  }
//}
