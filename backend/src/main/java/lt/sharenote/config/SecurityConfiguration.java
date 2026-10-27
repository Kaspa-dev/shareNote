package lt.sharenote.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    // Keys are process-local: restarting the API invalidates both token types.
    @Bean KeyPair signingKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Bean JwtEncoder jwtEncoder(KeyPair signingKey) {
        var key = new RSAKey.Builder((RSAPublicKey) signingKey.getPublic())
                .privateKey((RSAPrivateKey) signingKey.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    }

    @Bean @Primary JwtDecoder jwtDecoder(KeyPair signingKey) {
        return decoder(signingKey, "access");
    }

    @Bean JwtDecoder refreshJwtDecoder(KeyPair signingKey) {
        return decoder(signingKey, "refresh");
    }

    private JwtDecoder decoder(KeyPair signingKey, String purpose) {
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) signingKey.getPublic()).build();
        OAuth2TokenValidator<Jwt> purposeValidator = jwt -> {
            boolean valid = purpose.equals(jwt.getClaimAsString("token_use")) &&
                    jwt.getExpiresAt() != null && jwt.getSubject() != null &&
                    jwt.getSubject().matches("[1-9][0-9]*") &&
                    (!purpose.equals("refresh") || (jwt.getId() != null && !jwt.getId().isBlank()));
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid token purpose or required claims.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("sharenote"), purposeValidator));
        return decoder;
    }

    @Bean SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper) throws Exception {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("role");
        roles.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("USER", "EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/organizations/*/groups/*/notes").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/organizations/*/groups/*/notes/*").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/organizations/*/groups/*/notes/*").hasAnyRole("EDITOR", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> problem(mapper, response, 401))
                        .accessDeniedHandler((request, response, ex) -> problem(mapper, response, 403)))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, ex) -> problem(mapper, response, 401))
                        .accessDeniedHandler((request, response, ex) -> problem(mapper, response, 403)))
                .build();
    }

    private static void problem(ObjectMapper mapper, HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        if (status == 401) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(
                HttpStatus.valueOf(status), status == 401 ? "A valid Bearer token is required." : "Your role does not allow this operation."));
    }
}
