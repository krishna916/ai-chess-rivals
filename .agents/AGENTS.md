# AI Chess Rivals — Local Development Rules

This file outlines constraints and debugging procedures for local development on this project, with a specific focus on Windows environments.

## 1. Port Allocation and Bind Conflicts

To avoid conflicts with native database instances (such as a local PostgreSQL installation) and Windows kernel port exclusions:

- **Local PostgreSQL Container Port**: Always map the host port to `5433` (e.g. `5433:5432`) in `docker-compose.yml`. Do not map to the default `5432` on the host.
- **Local Spring Boot Container Port**: Always map the host port to `8082` (e.g. `8082:8080`) in `docker-compose.yml`. Do not map to the default `8080` on the host.
- **Spring Fallbacks**: Default configuration fallback URLs in `application.yaml` (such as `spring.datasource.url`) must point to `localhost:5433` to match the local container mapping.

---

## 2. Debugging Port Failures on Windows

If a service fails to bind with an access permissions error (e.g., `An attempt was made to access a socket in a way forbidden by its access permissions`):

1. **Check Excluded Ranges**: Run the following PowerShell command to check if Windows/Hyper-V has reserved the port range:
   ```powershell
   netsh int ipv4 show excludedportrange protocol=tcp
   ```
2. **Resolution**: If the target port is inside an excluded range, update `docker-compose.yml` to map to a host port outside the ranges listed.

If a local connection fails with `password authentication failed` or connects to the wrong database despite Docker running:

1. **Check Dual Stack Bindings**: Run the following PowerShell command to see if a native service is binding to IPv4 while Docker is binding to IPv6:
   ```powershell
   Get-NetTCPConnection -LocalPort <port-number>
   ```
2. **Resolution**: Stop the conflicting native service (e.g. `Stop-Service -Name "postgresql-x64-15"` if run as admin) or connect explicitly using a non-default host port mapping.
