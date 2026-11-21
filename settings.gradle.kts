rootProject.name = "unbutu-platform"

include(
    "libs:platform-starter",
    "libs:event-contracts",
    "libs:outbox",
    "services:api-gateway",
    "services:identity-service",
    "services:catalog-service",
    "services:inventory-service",
    "services:booking-service",
    "services:payment-service",
    "services:notification-service",
)
