package demo.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The subset of the Petstore "Pet" schema that our consumer actually uses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Pet(long id, String name, String status) {
}
