# Valkey GLIDE
**Valkey GLIDE** is an open-source, reliable, performance-optimized, user-oriented, official Valkey client library.  

It puts emphasis on reliability, fault tolerance, high availability, performance optimization, and best practices for using Valkey.  
Sponsored and supported by [AWS](https://aws.amazon.com/about-aws/whats-new/2024/07/valkey-glide-open-source-valkey-redis/) and [GCP](https://cloud.google.com/blog/products/databases/announcing-memorystore-for-valkey#:~:text=With%20so%20much%20innovation%20in%20Valkey%208.0%2C%20open%2Dsource%20enhancements%20like%20vector%20search%20and%20JSON%2C%20and%20for%20client%20libraries%20(see%20GLIDE)%2C%20it%E2%80%99s%20clear%20that%20Valkey%20is%20going%20to%20be%20a%20game%2Dchanger%20in%20the%20high%2Dperformance%20data%20management%20space.%C2%A0), the project leverages the expertise, lessons learned, and familiarity with real users' needs.  
Expertise which developed over a decade of experience in building and operating large-scale Redis-OSS compatible services.

The project is **community-centered** and community-driven.  
It is **open source**, part of the [valkey-io](https://valkey.io/) organization, and we welcome and encourage contributions from the community.  
It puts users first, and we are committed to providing the best experience for users.  
It aims to fast-follow Valkey releases and Valkey new features, providing users the best experience with Valkey.  

The core of the library is written in Rust, providing the best performance, security, and reliability.  
This design allows the library to be used in multiple languages.  
The design also allows the library to achieve fast development, ensuring quick iterations and feature enhancements.  

*GLIDE stands for General Language Independent Driver for the Enterprise*

## Getting Started - Current Language Support
Valkey GLIDE supports language bindings for the following programming languages:
### Supported Languages
- ☕ [Java](./java/README.md) - [Getting Started and docs](https://github.com/valkey-io/valkey-glide/wiki/Java-Wrapper) - [Java Examples](./examples/java/) - [Kotlin Examples](./examples/kotlin/) - [Scala Examples](./examples/scala/) - [Maven](https://central.sonatype.com/artifact/io.valkey/valkey-glide)
- 🐍 [Python](./python/README.md) - [Getting Started and docs](https://github.com/valkey-io/valkey-glide/wiki/Python-wrapper) - [Examples](./examples/python/) - [PyPI](https://pypi.org/project/valkey-glide/)
- 🐢 [Node](./node/README.md) - [Getting Started and docs](https://github.com/valkey-io/valkey-glide/wiki/NodeJS-wrapper) - [Examples](./examples/node/) - [NPM](https://www.npmjs.com/package/@valkey/valkey-glide)
### Public Preview
- 🐱 [Go](./go/README.md) - [Getting Started and docs](https://github.com/valkey-io/valkey-glide/wiki/Golang-wrapper) - [Package](https://pkg.go.dev/github.com/valkey-io/valkey-glide/go/api)
### Under Active Development
- C# -> [Join the effort](./csharp/DEVELOPER.md)
### Initial Development
- C++ -> [Join the effort](https://github.com/mortymacs/valkey-glide)
- Ruby (Design process)

🦦 And more to come...
### [Further Reading - General Concepts](https://github.com/valkey-io/valkey-glide/wiki/General-Concepts)

## Supported Engine Versions
Valkey GLIDE is API-compatible with the following engine versions:

| Engine Type           |  6.2  |  7.0  |  7.2  |  8.0  |
|-----------------------|-------|-------|-------|-------|
| Valkey                |   -   |   -   |   V   |   V   |
| Redis                 |   V   |   V   |   V   |   -   |

## Current Status and upcoming releases
### Current Status
Currently Valkey GLIDE is supported in Java, Python, Node and Go (*Public preview*).  
The library is in active development, and we are working on adding more features and languages.  
Our leading value is to provide what's best for the users, so each release is re-evaluated and prioritized based on user needs, and may change accordingly.  
The next releases will be focused on adding more features and improving the performance and reliability of the library, along with adding more language bindings.  
### Upcoming Releases
#### v1.4 (May 2025)
- GO general availability
- Open Telemetry support
- Batching support
### Previous Releases
#### v1.3 (Feb. 2025)
- Public preview for GO support
#### v1.2 (Dec. 2024)
- Vector Similarity Search and JSON modules support
- Availability zone routing for Read from Replica

## Getting Help
If you have any questions, feature requests, encounter issues, or need assistance with this project, please don't hesitate to open a GitHub issue. Our community and contributors are here to help you. Before creating an issue, we recommend checking the [existing issues](https://github.com/valkey-io/valkey-glide/issues) to see if your question or problem has already been addressed. If not, feel free to create a new issue, and we'll do our best to assist you. Please provide as much detail as possible in your issue description, including:

1. A clear and concise title
2. Detailed description of the problem or question
3. Reproducible test case or step-by-step instructions
4. Valkey GLIDE version in use
5. Operating system details
6. Server version
7. Cluster or standalone setup information, including topology, number of shards, number of replicas, and data types used
8. Relevant modifications you've made
9. Any unusual aspects of your environment or deployment
10. Log files

## Contributing

GitHub is a platform for collaborative coding. If you're interested in writing code, we encourage you to contribute by submitting pull requests from forked copies of this repository. Additionally, please consider creating GitHub issues for reporting bugs and suggesting new features. Feel free to comment on issues that interest. For more info see [Contributing](./CONTRIBUTING.md).

## License
* [Apache License 2.0](./LICENSE)
