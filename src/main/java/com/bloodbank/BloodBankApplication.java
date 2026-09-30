package com.bloodbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Entry point of the Blood Bank Donor Matching Portal.
 *
 * <p>Extending {@link SpringBootServletInitializer} is what allows the single
 * {@code bloodbank.war} artefact to be run three ways without rebuilding:
 * <ul>
 *   <li>{@code mvn spring-boot:run} — fast local development loop</li>
 *   <li>{@code java -jar target/bloodbank.war} — executable WAR, embedded Tomcat</li>
 *   <li>dropped into a Tomcat 10.1 container as {@code ROOT.war} — the deployed form</li>
 * </ul>
 * That portability is requirement NFR-10 in {@code docs/SRS.md}.
 */
@SpringBootApplication
public class BloodBankApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(BloodBankApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(BloodBankApplication.class, args);
    }
}
