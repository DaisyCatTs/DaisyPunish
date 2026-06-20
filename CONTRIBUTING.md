# Contributing

Use Java 21 and run `./gradlew build apiCheck` before opening a change. New provider adapters must live in their own module, declare their provider license, use only supported public APIs, and pass the shared provider contract. Do not add raw database access, reflection against private provider internals, blocking main-thread calls, or silent failure fallbacks.
