package lt.sharenote;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@io.swagger.v3.oas.annotations.security.SecurityScheme(name = "bearerAuth", type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@OpenAPIDefinition(servers = @Server(url = "/"), security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth"), info = @Info(title = "ShareNote API", version = "2.0.0",
        description = "JWT authentication. USER, EDITOR and ADMIN can read. EDITOR can create notes and update or delete only their own notes (JWT sub equals ownerId). ADMIN can manage all resources. Laboratory work 2."))
public class ShareNoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShareNoteApplication.class, args);
    }
}
