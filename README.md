# dps-tck

Technology Compatibility Kit for the Dataplane Signaling Protocol (DPS). This project verifies that a control plane implementation correctly implements the DPS specification by executing a suite of compliance tests against it.

Specs: https://eclipse-dataplane-signaling.github.io/dataplane-signaling/v1.0-RC2

## Overview

The DPS-TCK is part of the Eclipse Dataspace TCK project. It tests both consumer and provider control plane roles by orchestrating protocol message exchanges and validating that the system under test sends and receives the correct DPS messages with valid payloads.

Tests are organized by scenario identifier (e.g. `CP_C:01-01`, `CP_P:02-01`) and annotated as mandatory compliance tests. All tests target the `base-compliance` tag.

## Running the TCK

### JUnit mode

The recommended way to run the TCK against a JVM-based control plane is to embed it in a JUnit test. Add the following dependency to your test module:

```kotlin
testImplementation("org.eclipse.dataspacetck.dps:dps-tck:<version>")
```
NOTE: check on [Maven Central](https://central.sonatype.com/artifact/org.eclipse.dataspacetck.dps/dps-tck) to get the latest version number

Then write a JUnit 5 test that starts your runtime, configures the TCK properties, and asserts that all tests pass:

```java
import org.eclipse.dataspacetck.dps.system.DpsSystemLauncher;
import org.eclipse.dataspacetck.runtime.TckRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DpsTckTest {

    // Ports where your control plane will listen
    private static final String WEBHOOK_URL = "http://localhost:9191/api/v1/dataflows";
    private static final String PROTOCOL_URL = "http://localhost:8282/api/v1/dsp";

    @Timeout(300)
    @Test
    void assertDpsCompliance() {
        // Start your runtime under test here (e.g. via a JUnit extension or @BeforeAll)

        var result = TckRuntime.Builder.newInstance()
                .properties(Map.of(
                        "dataspacetck.dps.controlplane.webhook.url", WEBHOOK_URL,
                        "dataspacetck.dps.controlplane.protocol.url", PROTOCOL_URL
                ))
                .launcher(DpsSystemLauncher.class)
                .addPackage("org.eclipse.dataspacetck.dps.verification.controlplane") // or .dataplane if you are testing a data-plane
                .build()
                .execute();

        assertThat(result.getFailures()).isEmpty();
    }
}
```

Use a JUnit extension (e.g. `@RegisterExtension`) to start and stop your runtime before the test runs, and bind the actual port numbers once the runtime is up.

### Docker mode

A pre-built image is published to Docker Hub as `eclipsedataspacetck/dps-tck-runtime`.

Mount a configuration file at `/etc/tck/config.properties` inside the container:

```bash
docker run --rm \
  -v /path/to/config.properties:/etc/tck/config.properties \
  eclipsedataspacetck/dps-tck-runtime
```

A sample configuration file is provided at `config/tck/sample.tck.properties`. Copy and edit it to match your environment, then pass it to the container as shown above.

If the control plane runs on the host machine, use `host.docker.internal` (macOS/Windows) or the host's bridge IP (Linux, typically `172.17.0.1`) in place of `localhost` in the URL properties.

## Configuration

When running in Docker mode, configuration is loaded from the properties file mounted at `/etc/tck/config.properties`. A sample is provided at `config/tck/sample.tck.properties`.

When using the `TckRuntime` API in a JUnit test, properties are passed directly via `TckRuntime.Builder.properties(Map)`.

In both cases, environment variables are also read: prefix `dataspacetck.` with `DATASPACETCK_` (dots replaced with underscores, uppercased) to override any property.

| Property                                     | Default                                     | Description                                                                                  |
|----------------------------------------------|---------------------------------------------|----------------------------------------------------------------------------------------------|
| `dataspacetck.test.package`                  | `org.eclipse.dataspacetck.dps.verification` | The test package to be executed (see [Test Coverage](#test-coverage) for details             |
| `dataspacetck.dps.controlplane.webhook.url`  |                                             | Webhook URL the TCK uses to send signaling messages to the consumer control plane under test |
| `dataspacetck.dps.controlplane.protocol.url` |                                             | DSP protocol endpoint of the provider control plane under test                               |
| `dataspacetck.dps.dataplane.url`             |                                             | DPS endpoint of the data plane under test                                                    |
| `dataspacetck.dps.dataplane.authorization`   | `dummy-authorization`                       | Authorization token sent in requests to the data plane                                       |
| `dataspacetck.dps.default.wait`              | `15`                                        | Timeout in seconds to wait for expected messages                                             |
| `dataspacetck.debug`                         | `false`                                     | Enable verbose debug logging                                                                 |
| `dataspacetck.launcher`                      | `DpsSystemLauncher`                         | System launcher class (override for custom bootstrap)                                        |

### Control-plane test agreement IDs

Each control-plane test method has a configurable `agreementId` property. The key is derived from the test method name and the field name, uppercased and joined with `_` — so test `cp_c_01_01` uses property `CP_C_01_01_AGREEMENTID`. When no value is configured the TCK generates a random UUID, which will not match any agreement in a real control plane.

| Property               | Description                                               |
|------------------------|-----------------------------------------------------------|
| `CP_C_01_01_AGREEMENTID` | Contract agreement ID for consumer signaling test 01-01 |
| `CP_C_01_02_AGREEMENTID` | Contract agreement ID for consumer signaling test 01-02 |
| `CP_...` | ... and so on |

Currently all tests can share a single agreement; set every property to the same value.

### Contract agreement requirements

Control-plane tests require a contract agreement to exist in the system under test **before the tests run**. The agreement must satisfy two conditions:

1. **Agreement ID** — matches the value configured via the `*_AGREEMENTID` properties above.
2. **Participant IDs** — the agreement's consumer and provider IDs must match the participant IDs the TCK uses when sending DSP messages (see below).

Control planes typically validate that the sender of a DSP message is the participant named in the agreement. If the IDs do not match, messages will be rejected and the tests will fail.

### TCK participant IDs

When the TCK sends DSP messages it identifies itself as a participant. Each test class exposes a `tckParticipantId` field, also configurable as a property (key: `<TEST_METHOD_NAME>_TCKPARTICIPANTID`):

| Test class | Role the TCK plays | Default `tckParticipantId` | Meaning |
|---|---|---|---|
| `ControlPlaneProviderSignalingTest` (CP_P) | Consumer control plane | `consumerId` | The TCK sends `TransferRequestMessage` as the consumer — the agreement must name `consumerId` as its consumer participant |
| `ControlPlaneConsumerSignalingTest` (CP_C) | Provider control plane | `providerId` | The TCK sends `TransferStartMessage` etc. as the provider — the agreement must name `providerId` as its provider participant |

If your system already has a contract agreement with different participant IDs you can configure the TCK to use those instead of the defaults. For example, if the agreement already has `my-consumer` and `my-provider` as participant IDs:

```properties
# For CP_P tests (TCK acts as consumer)
CP_P_01_01_TCKPARTICIPANTID=my-consumer
CP_P_01_02_TCKPARTICIPANTID=my-consumer
# ... and so on for each test

# For CP_C tests (TCK acts as provider)
CP_C_01_01_TCKPARTICIPANTID=my-provider
CP_C_01_02_TCKPARTICIPANTID=my-provider
# ... and so on for each test
```

Alternatively, create the contract agreement with the default IDs (`consumerId` and `providerId`) so no extra configuration is needed.


## Test coverage

Test packages available:
- `org.eclipse.dataspacetck.dps.verification.controlplane` — control plane tests (CP_C, CP_P)
- `org.eclipse.dataspacetck.dps.verification.dataplane` — all data plane tests (DP_C and DP_P, pull and push)
- `org.eclipse.dataspacetck.dps.verification.dataplane.pull` — data plane pull-flow tests only
- `org.eclipse.dataspacetck.dps.verification.dataplane.push` — data plane push-flow tests only

## License

Apache License 2.0
