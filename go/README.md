# GO wrapper

The Valkey GLIDE Go Wrapper is currently in **public preview.** Please refer to [this page](https://pkg.go.dev/github.com/valkey-io/valkey-glide/go/v2) for available commands.

# Valkey GLIDE

Valkey General Language Independent Driver for the Enterprise (GLIDE), is an open-source Valkey client library. Valkey GLIDE is one of the official client libraries for Valkey, however the public preview currently has limited implementation for the commands. Valkey GLIDE supports Valkey 7.2 and above, and Redis open-source 6.2, 7.0 and 7.2. Application programmers use Valkey GLIDE to safely and reliably connect their applications to Valkey- and Redis OSS- compatible services. Valkey GLIDE is designed for reliability, optimized performance, and high-availability, for Valkey and Redis OSS based applications. It is sponsored and supported by AWS and GCP, and is pre-configured with best practices learned from over a decade of operating Redis OSS-compatible services used by hundreds of thousands of customers. To help ensure consistency in application development and operations, Valkey GLIDE is implemented using a core driver framework, written in Rust, with language specific extensions. This design ensures consistency in features across languages and reduces overall complexity.

## Supported Engine Versions

Refer to the [Supported Engine Versions table](https://github.com/valkey-io/valkey-glide/blob/main/README.md#supported-engine-versions) for details.

# Getting Started - GO Wrapper

## System Requirements

The release of Valkey GLIDE was tested on the following platforms:

Linux:

-   Ubuntu 20 (x86_64/amd64 and arm64/aarch64)
-   Amazon Linux 2 (AL2) and 2023 (AL2023) (x86_64)

**Note: Currently Alpine Linux / MUSL is NOT supported.**

macOS:

- macOS 14.7 (Apple silicon/aarch_64)
- macOS 13.7 (x86_64/amd64)

## GO supported versions

Valkey GLIDE Go supports Go version 1.22 and above.

## Installation and Setup

To install Valkey GLIDE in your Go project, follow these steps:

1. Open your terminal in your project directory.
2. Execute the commands below:
    ```bash
    $ go get github.com/valkey-io/valkey-glide/go/v2
    $ go mod tidy
    ```
3. After installation, you can start up a Valkey server and run one of the examples in [Basic Examples](#basic-examples).


## Basic Examples


### Standalone Example:

```go
package main

import (
	"fmt"

    glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
	host := "localhost"
	port := 6379

	config := config.NewGlideClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port})

	client, err := glide.NewGlideClient(config)
	if err != nil {
        fmt.Println("There was an error: ", err)
        return
	}

	res, err := client.Ping()
	if err != nil {
        fmt.Println("There was an error: ", err)
        return
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Cluster Example:

```go
package main

import (
	"fmt"

    glide "github.com/valkey-io/valkey-glide/go/v2"
	"github.com/valkey-io/valkey-glide/go/v2/config"
)

func main() {
	host := "localhost"
	port := 7001

	config := config.NewGlideClusterClientConfiguration().
		WithAddress(&config.NodeAddress{Host: host, Port: port})

	client, err := glide.NewGlideClusterClient(config)
	if err != nil {
		fmt.Println("There was an error: ", err)
		return
	}

	res, err := client.Ping()
	if err != nil {
        fmt.Println("There was an error: ", err)
        return
	}
	fmt.Println(res) // PONG

	client.Close()
}
```

### Building & Testing

Development instructions for local building & testing the package are in the [DEVELOPER.md](DEVELOPER.md) file.
