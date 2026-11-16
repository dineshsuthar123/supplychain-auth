package com.supplychain.productservice.config;

import org.flywaydb.core.api.callback.*;
import org.springframework.context.annotation.*;
import java.sql.SQLException;

@Configuration
@Profile("oracle")
public class OracleMigrationGrants {
    @Bean Callback restrictRuntimeMetadata() {
        return new Callback() {
            public boolean supports(Event event, Context context) { return event == Event.AFTER_MIGRATE; }
            public boolean canHandleInTransaction(Event event, Context context) { return true; }
            public String getCallbackName() { return "restrict-runtime-migration-history"; }
            public void handle(Event event, Context context) {
                try (var statement = context.getConnection().createStatement()) {
                    statement.execute("REVOKE ALL ON public.flyway_schema_history FROM supplyprint_app");
                } catch (SQLException error) { throw new IllegalStateException("Cannot restrict migration metadata", error); }
            }
        };
    }
}
