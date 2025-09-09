# JNI Protocol Guide 🚀

A friendly guide to understanding how Java and Rust communicate in our high-performance Valkey client.

## What is this Protocol? 🤔

Think of it as a **language translator** between Java and Rust! When your Java application wants to talk to Valkey, it needs to send instructions through our JNI bridge to the Rust layer. This protocol is simply the "format" we use to package those instructions.

## The Big Picture 📊

```
Java App → [Package Request] → JNI Bridge → [Unpack & Execute] → Rust Layer → Valkey Server
                    ↓                                     ↓
              Binary Protocol                    glide-core commands
```

## Protocol Structure 🏗️

### Single Commands
When you run a simple command like `client.set("key", "value")`, here's what travels through the wire:

```
┌─────────────────┐
│ Command Request │
├─────────────────┤
│ • command_name  │  (e.g., "SET")
│ • arguments     │  (e.g., ["key", "value"])
│ • route_info    │  (where to send it)
│ • binary_output │  (string or binary result?)
└─────────────────┘
```

### Batch Commands (The Powerful Stuff! 💪)
When you want to run multiple commands together:

```
┌─────────────────┐
│ Batch Request   │
├─────────────────┤
│ • commands[]    │  List of commands to run
│ • atomic        │  Run as transaction? (MULTI/EXEC)
│ • timeout_ms    │  How long to wait (0 = use default)
│ • raise_on_error│  Stop on first error?
│ • route         │  Which server in cluster?
│ • binary_output │  Binary or string results?
└─────────────────┘
```

## Binary Format (The Technical Bits) 🔧

Don't worry - you never see this directly! But here's how data gets packed:

### String Fields
```
Length (4 bytes) + Content (UTF-8 bytes)
```

### Boolean Fields
```
1 byte: 0x01 = true, 0x00 = false
```

### Numbers
```
4 bytes (little-endian)
```

### Lists (Arrays)
```
Count (4 bytes) + Item1 + Item2 + ... + ItemN
```

## Real Examples 📝

### Simple SET Command
```java
// Java side - what you write:
client.set("user:123", "john_doe");

// What gets sent (conceptually):
{
  command_name: "SET",
  arguments: ["user:123", "john_doe"],
  route_info: null,
  binary_output: false
}
```

### Batch Operation
```java
// Java side - what you write:
List<Command> commands = Arrays.asList(
    new Command("SET", "key1", "value1"),
    new Command("SET", "key2", "value2")
);
client.executeBatch(commands, true); // atomic = true

// What gets sent (conceptually):
{
  commands: [
    { name: "SET", args: ["key1", "value1"] },
    { name: "SET", args: ["key2", "value2"] }
  ],
  atomic: true,          // Use MULTI/EXEC transaction
  timeout_ms: 0,         // Use client's default timeout
  raise_on_error: true,  // Stop if any command fails
  route: null,           // Let cluster decide routing
  binary_output: false   // Return strings, not bytes
}
```

## Why This Design? 🎯

### Performance Benefits
- **Zero-copy**: Large data stays in native memory when possible
- **Efficient packing**: Minimal overhead in the binary format
- **Batch processing**: Multiple commands in one round-trip

### Flexibility Features
- **Atomic vs Pipeline**: Choose transaction safety vs speed
- **Timeout control**: Per-request timeout handling
- **Error handling**: Continue or stop on errors
- **Binary support**: Handle raw bytes efficiently

## Timeout Magic ⏰

A special note about timeouts - they're really simple:

```java
timeout_ms = 0     // "Use whatever timeout the client was configured with"
timeout_ms = 5000  // "Wait maximum 5 seconds for this operation"
```

On the Rust side:
```rust
0 → None           // Use client default
5000 → Some(5000)  // Use specific timeout
```

## Route Information 🗺️

For cluster deployments, you can tell commands where to go using `RouteInfo`:

```java
// Let cluster decide (recommended - glide-core handles it automatically)
RouteInfo route = null;

// Send to all nodes in cluster
RouteInfo route = RouteInfo.allNodes();

// Send to all primary nodes only
RouteInfo route = RouteInfo.allPrimaries();

// Send to a random node
RouteInfo route = RouteInfo.random();

// Route based on key's hash slot (to primary)
RouteInfo route = RouteInfo.bySlotKey("user:123", false);

// Route based on key's hash slot (to replica for read-only commands)
RouteInfo route = RouteInfo.bySlotKey("user:123", true);

// Route to specific slot ID
RouteInfo route = RouteInfo.bySlotId(1234, false);

// Route to specific node by address
RouteInfo route = RouteInfo.byAddress("10.0.1.5", 6379);
```

### Real Usage Examples

```java
// Execute INFO command on all nodes
ClusterBatch batch = new ClusterBatch();
batch.info().withRoute(RouteInfo.allNodes());
client.executeBatch(batch.getCommands());

// Get value from replica for read performance
String value = client.get("frequently_read_key", 
                         RouteInfo.bySlotKey("frequently_read_key", true));

// Execute maintenance command on specific node
client.configSet("save", "900 1", RouteInfo.byAddress("10.0.1.5", 6379));
```

### How Routes Travel as Binary Data 📦

Here's a simple example showing how a route goes from Java code to binary data that Rust reads:

#### Java Side - What You Write:
```java
RouteInfo route = RouteInfo.bySlotKey("user:123", true);  // Route to replica
```

#### Java Side - Binary Conversion:
```java
// RouteInfo.toBytes() creates this binary data:
byte[] routeBytes = route.toBytes();
```

#### Binary Format (What Actually Gets Sent):
```
[2]           - Route type (SLOT_KEY = 2)
[8]           - Length of key string
[u][s][e][r][:][1][2][3]  - UTF-8 bytes of "user:123"
[1]           - Replica flag (true = 1)
```

#### Rust Side - What Gets Received:
```rust
// command_parser.rs reads this as:
let route_type = reader.read_u8("route_type")?;  // Gets 2 (SLOT_KEY)
let key = reader.read_string("slot_key")?;       // Gets "user:123"
let replica = reader.read_bool("replica")?;      // Gets true

// Converts to glide-core Route enum:
Route::SlotKeyRoute {
    slot_key: "user:123".to_string(),
    slot_type: if replica { SlotType::Replica } else { SlotType::Primary }
}
```

#### Different Route Types in Binary:

**Simple Route (Random):**
```java
RouteInfo.random()  →  [2] (just the type byte, no extra data)
```

**Node Address Route:**
```java
RouteInfo.byAddress("10.0.1.5", 6379)  →  [5][9][1][0].[0].[1].[5][25][15] (type + host + port)
```

**All Nodes Route:**
```java
RouteInfo.allNodes()  →  [0] (just the type byte)
```

## Error Handling 🛡️

The protocol handles errors gracefully:

- **Parse errors**: "I don't understand this request format"
- **Command errors**: "Valkey said this command failed" 
- **Timeout errors**: "This took too long"
- **Connection errors**: "Can't reach Valkey server"

## Behind the Scenes 🎭

When you call a command:

1. **Java** packages your request into binary format
2. **JNI Bridge** passes bytes to Rust (super fast!)
3. **Rust** unpacks the binary data using our protocol
4. **glide-core** executes the actual Valkey commands
5. **Results** flow back through the same pipeline

## Performance Notes 📈

This protocol is designed for speed:

- **1.8-2.9x faster** than our old socket-based approach
- **Handles 90k+ operations per second**
- **Efficient memory usage** with smart large-data handling
- **Low latency** with minimal conversion overhead

---

*This protocol powers the high-performance JNI implementation - but as a user, you just call simple Java methods! The complexity is hidden so you can focus on building amazing applications.* ✨