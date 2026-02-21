## 2026-02-21 - Prevent Stack Trace Leakage in Logs
**Vulnerability:** Default logging configuration unconditionally appended full stack traces to error messages.
**Learning:** Security by default requires careful consideration of what is logged. Stack traces are valuable for debugging but sensitive for production logs.
**Prevention:** Gate stack trace logging behind explicit 'debug' or 'trace' levels.
