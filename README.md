# dps-tck

Technology Compatibility Kit for the Dataplane Signaling Protocol (DPS). This project verifies that a control plane implementation correctly implements the DPS specification by executing a suite of compliance tests against it.

## Overview

The DPS-TCK is part of the Eclipse Dataspace TCK project. It tests both consumer and provider control plane roles by orchestrating protocol message exchanges and validating that the system under test sends and receives the correct DPS messages with valid payloads.

Tests are organized by scenario identifier (e.g. `CP_C:01-01`, `CP_P:02-01`) and annotated as mandatory compliance tests. All tests target the `base-compliance` tag.

## Prerequisites

- Java 17+
- Gradle 9.5+ (or use the included wrapper)

## Build

```bash
./gradlew build
```

Run only tests:

```bash
./gradlew test
```

Run checkstyle:

```bash
./gradlew checkstyleMain checkstyleTest
```

## Running the TCK

The TCK can run in two modes: local (in-process, no external services) and HTTP (against a running control plane).

### Local mode

Local mode embeds a simulated control plane and runs the full test suite in-process. This is useful for verifying the TCK itself.

```bash
./gradlew :dps-tck:test
```

### HTTP mode

To run the TCK against an external control plane, provide a configuration file:

```bash
java -jar dps-tck.jar -config path/to/config.properties
```

Or invoke the main class directly:

```
org.eclipse.dataspacetck.dps.DpsTckSuite
```

## Configuration

Configuration can be provided via a `.properties` file passed with `-config`, or via environment variables using the prefix `TCK_DPS_` (dots replaced with underscores, uppercased).

| Property | Default | Description |
|---|---|---|
| `tck.dps.local.connector` | `false` | Use the in-process connector instead of HTTP |
| `tck.dps.controlplane.webhook.url` | | Webhook URL the TCK exposes to the control plane under test |
| `tck.dps.controlplane.protocol.url` | | DSP protocol endpoint of the control plane under test |
| `tck.dps.default.wait` | `15000` | Timeout in milliseconds to wait for expected messages |
| `tck.debug` | `false` | Enable verbose debug logging |
| `tck.dps.test.package` | all verification tests | Restrict which test packages are executed |
| `tck.launcher` | `DpsSystemLauncher` | System launcher class (override for custom bootstrap) |

## Test coverage

### Consumer control plane (CP_C)

| ID | Description |
|---|---|
| CP_C:01-01 | DataFlowPrepareMessage dispatched, data flow completes |
| CP_C:01-02 | DataFlowPrepareMessage dispatched, data flow terminated |
| CP_C:02-01 | DataFlowSuspendMessage and DataFlowResumeMessage dispatched, data flow completes |
| CP_C:02-02 | DataFlowSuspendMessage dispatched, data flow terminated |
| CP_C:03-01 | Asynchronous DataFlowPrepareMessage, CP responds 202 PREPARING |

### Provider control plane (CP_P)

| ID | Description |
|---|---|
| CP_P:01-01 | DataFlowStartMessage dispatched, data flow completes |
| CP_P:01-02 | DataFlowStartMessage dispatched, data flow terminated |
| CP_P:02-01 | DataFlowSuspendMessage and DataFlowResumeMessage dispatched, data flow completes |
| CP_P:02-02 | DataFlowSuspendMessage dispatched, data flow terminated |
| CP_P:03-01 | Asynchronous DataFlowStartMessage, CP responds 202 STARTING |

## Architecture

```
DpsTckSuite            -- CLI entry point, loads config, runs JUnit platform
DpsSystemLauncher      -- Bootstrap container, instantiates pipelines and clients
ControlPlaneSignalingPipeline  -- Fluent DSL for orchestrating message sequences
ControlPlaneClient     -- Interface for triggering actions on the system under test
HttpControlPlaneClient -- OkHttp3-based HTTP implementation
LocalControlPlaneConnector -- Embedded control plane for local testing
DpsMessage             -- Enum of DPS message types with JSON schema validators
```

Message payloads are validated against JSON schemas located in `src/main/resources/schema/`, targeting the `dspace-sig v1.0` namespace.

## License

Apache License 2.0
