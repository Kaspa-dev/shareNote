package lt.sharenote;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(servers = @Server(url = "/"), info = @Info(title = "ShareNote API", version = "1.0.0",
        description = "15 CRUD operations for organizations, groups, and notes. Laboratory work 1."))
public class ShareNoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShareNoteApplication.class, args);
    }
}
