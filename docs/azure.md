# Azure lab demo

The deployment uses the **Azure for Students** subscription, resource group
`rg-sharenote-demo`, VM `vm-sharenote-demo`, and Sweden Central.
The public URL is https://sharenote-4d31a374.swedencentral.cloudapp.azure.com.

## Start and stop

Run from the repository root:

```powershell
./scripts/azure-vm.ps1 Start
./scripts/azure-vm.ps1 Status
./scripts/azure-vm.ps1 Stop
```

`Stop` deallocates the VM and stops compute charges. Disk and static public IP
charges continue. Containers restart automatically when the VM starts; allow
a minute for the Java API to become ready. Existing accounts and notes survive
stop/start. API restarts invalidate access JWTs; the browser obtains a new JWT
automatically while its refresh token remains valid.

There is no automatic shutdown: stop the VM when you finish using the demo.

## Credentials and access

The administrator username is `admin`. The generated password is stored under
`ADMIN_PASSWORD` in the local, Git-ignored `tmp/azure-demo.env` file. This file
also contains the database password; keep it private. A copy is installed as
`/opt/sharenote/deploy/.env` on the VM with mode 600.

The dedicated SSH key is `tmp/sharenote-azure`. SSH is restricted by the Azure
network security group to the deployment computer's public IP. If that address
changes, update the `SSH` rule in `nsg-sharenote` before connecting.

```powershell
ssh -i tmp/sharenote-azure azureuser@sharenote-4d31a374.swedencentral.cloudapp.azure.com
```

On the VM, inspect services with:

```sh
cd /opt/sharenote/deploy
sudo docker compose -f compose.azure.yaml ps
sudo docker compose -f compose.azure.yaml logs --tail 100 api
```

## Architecture and costs

An Ubuntu 24.04 `Standard_B2als_v2` VM runs Caddy, the React/Nginx frontend,
the Java API, and PostgreSQL 17 using `deploy/compose.azure.yaml`.
Caddy obtains and renews the HTTPS certificate. Only ports 80 and 443 are
public; SSH is restricted, and database/API ports are internal to Docker.
PostgreSQL data and certificate state persist in Docker volumes on the OS disk.
The database starts with the repository's Flyway demo data. No local accounts
or notes are copied. This is a single-VM lab deployment without managed backups.

Azure retail EUR rates checked on 2026-09-27, without free allowances:

| Item | Estimated rate |
| --- | ---: |
| VM while allocated | EUR 0.0334/hour |
| Standard static IPv4 | EUR 0.0043/hour |
| 32 GB Standard HDD | EUR 1.3189/month |

At 730 hours of IP allocation and 100 VM running hours, the estimate is
**EUR 7.80/month**, before tax, disk transactions, and network usage.
Use roughly 100 hours/month or less to leave headroom within the EUR 10 budget.
This is an estimate, not a spending cap; actual subscription billing and credits
can differ. Leaving the VM running continuously exceeds the budget.
Rates come from the [Azure Retail Prices API](https://learn.microsoft.com/rest/api/cost-management/retail-prices/azure-retail-prices).

## Infrastructure and updates

`deploy/azuredeploy.json` defines the VM, disk, public IP, virtual network,
network interface, and firewall rules. `deploy/cloud-init.yaml` installs Docker
and Compose and enables Docker at boot. Local deployment parameters are saved
in `tmp/azure-parameters.json`; no credentials are embedded in the template.

To update application code, archive `backend`, `frontend`, and `deploy`, excluding
`node_modules`, `target`, `dist`, test output, and `.env`. Upload and extract the
archive into `/opt/sharenote`, preserving the server's `deploy/.env`, then run:

```sh
cd /opt/sharenote/deploy
sudo docker compose -f compose.azure.yaml up -d --build
```

Never run `docker compose down -v` unless you intend to delete the database.
Deleting the Azure resource group also permanently removes this demo and its data.

## Verification

Verified on 2026-09-27: frontend production build, 6 backend integration tests,
4 browser tests, and 29 live HTTPS checks covering all 15 CRUD operations.
A real browser also verified the trusted HTTPS certificate, administrator login,
notes screen, and administrator controls without JavaScript errors. The live
CRUD checks removed their own records and preserved the seeded demo data.
