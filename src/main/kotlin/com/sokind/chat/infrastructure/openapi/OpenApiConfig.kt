package com.sokind.chat.infrastructure.openapi

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.StringSchema
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Sokind Chat API")
                .version("0.1.0")
                .description(
                    """
                    1:1 realtime chat with event-sourced state restoration.

                    - Source of truth = append-only `events` table.
                    - Idempotency: client must send a UUID `clientEventId` per logical event;
                      retries collapse to a single row and return the original `serverSeq`.
                    - Ordering: `server_seq` (BIGINT AUTO_INCREMENT) — arrival order, clock-skew safe.
                    - State restoration: `GET /sessions/{id}/timeline?at=...` folds events deterministically.
                    """.trimIndent()
                )
        )
        .components(
            Components().addHeaders(
                "X-User-Id",
                Header()
                    .description("Caller's user identifier (no authentication in this assignment).")
                    .schema(StringSchema())
            )
        )
}
