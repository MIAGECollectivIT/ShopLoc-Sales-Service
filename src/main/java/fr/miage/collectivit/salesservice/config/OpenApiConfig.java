package fr.miage.collectivit.salesservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI and Swagger configuration with security scheme and vendor extensions loaded from
 * application.yaml.
 */
@Configuration
public class OpenApiConfig {

  public static final String SECURITY_SCHEME_NAME = "BearerAuth";

  @Value("${application.openapi.title:ShopLoc - Sales Service API}")
  private String title;

  @Value("${application.openapi.description:Sales management REST API}")
  private String description;

  @Value("${application.openapi.version:1.0.0}")
  private String version;

  @Value("${application.openapi.contact.name:MIAGE Collectiv'IT}")
  private String contactName;

  @Value("${application.openapi.contact.email:contact@collectivit.miage.fr}")
  private String contactEmail;

  @Value("${application.openapi.contact.url:https://github.com/MIAGECollectivIT}")
  private String contactUrl;

  @Value("${application.openapi.license.name:MIT}")
  private String licenseName;

  @Value("${application.openapi.license.url:https://opensource.org/licenses/MIT}")
  private String licenseUrl;

  @Value("${application.security.auth-type:Bearer JWT}")
  private String authType;

  @Value("${application.security.description:JWT Bearer token authentication}")
  private String securityDescription;

  @Value("${application.openapi.extensions.x-api-audience:ShopLoc Internal Microservices}")
  private String apiAudience;

  @Value("${application.openapi.extensions.x-service-name:sales-service}")
  private String serviceName;

  @Value("${application.openapi.extensions.x-service-environment:development}")
  private String serviceEnvironment;

  @Bean
  public OpenAPI customOpenAPI() {
    Map<String, Object> extensions =
        Map.of(
            "x-api-audience", apiAudience,
            "x-service-name", serviceName,
            "x-service-environment", serviceEnvironment,
            "x-auth-type", authType);

    return new OpenAPI()
        .info(
            new Info()
                .title(title)
                .description(description)
                .version(version)
                .contact(new Contact().name(contactName).email(contactEmail).url(contactUrl))
                .license(new License().name(licenseName).url(licenseUrl))
                .extensions(extensions))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME_NAME,
                    new SecurityScheme()
                        .name(SECURITY_SCHEME_NAME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(securityDescription)));
  }
}
