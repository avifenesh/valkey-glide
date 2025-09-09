### JNI guard results (extracted from latest_jni_vs_uds_comparison.csv)

Source: `JNI-java/performance_charts/latest_jni_vs_uds_comparison.csv`

| Test Scenario  | JNI QPS | GET P50 | GET P99 |  CPU% | Mem (MB) |
| -------------- | ------: | ------: | ------: | ----: | -------: |
| 10k QPS @ 100B |   9,995 |  0.28ms |  2.04ms |  5.0% |       53 |
| 20k QPS @ 100B |  19,996 |  0.35ms |  1.98ms |  8.3% |       19 |
| 30k QPS @ 100B |  29,994 |  0.38ms |  1.99ms | 11.1% |       21 |
| 40k QPS @ 100B |  39,992 |  0.42ms |  2.04ms | 13.2% |       19 |
| 50k QPS @ 100B |  49,990 |  0.48ms |  2.10ms | 15.7% |       18 |
| 60k QPS @ 100B |  66,828 |  0.50ms |  2.57ms | 16.1% |     1409 |
| 90k QPS @ 100B |  89,990 |  0.67ms |  2.43ms | 18.4% |     2353 |
| 80k QPS @ 100B |  79,985 |  0.68ms |  2.42ms | 18.8% |       19 |
| 10k QPS @ 4KB  |  10,000 |  0.32ms |  2.02ms |  5.9% |     1799 |
| 20k QPS @ 4KB  |  19,996 |  0.41ms |  2.00ms |  9.6% |      579 |
| 30k QPS @ 4KB  |  29,995 |  0.43ms |  2.11ms | 12.9% |      466 |
| 40k QPS @ 4KB  |  39,976 |  0.56ms |  3.38ms | 15.6% |     1049 |
| 50k QPS @ 4KB  |  49,990 |  0.67ms |  2.50ms | 16.9% |      221 |
| 60k QPS @ 4KB  |  59,991 |  1.01ms |  2.72ms | 18.5% |      545 |
| 70k QPS @ 4KB  |  66,828 |  1.21ms |  3.79ms | 16.5% |     1931 |
| 10k QPS @ 25KB |   9,995 |  0.47ms |  2.19ms |  9.4% |      769 |

Note: This file serves as a guard baseline for JNI performance regressions.
