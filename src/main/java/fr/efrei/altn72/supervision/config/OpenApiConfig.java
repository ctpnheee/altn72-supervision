package fr.efrei.altn72.supervision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Documentation Swagger de l'API (F12). Les pages Thymeleaf n'y figurent pas. */
@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI supervisionOpenAPI() {
		return new OpenAPI().info(new Info()
				.title("Supervision ALTN72")
				.description("Consultation, affectation et résolution des incidents. La session du navigateur est exigée.")
				.version("v1"));
	}
}
