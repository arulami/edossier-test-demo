package demo.support;

/** One place for environment-dependent settings (override with -Dpetstore.baseUrl=...). */
public final class TestConfig {

    private TestConfig() {
    }

    public static String petstoreBaseUrl() {
        return System.getProperty("petstore.baseUrl", "http://localhost:8080/api/v3");
    }

    /** The live OpenAPI document published by the service under test. */
    public static String openApiSpecUrl() {
        return petstoreBaseUrl() + "/openapi.json";
    }
}
