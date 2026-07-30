$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

dotnet run --project src/LAMWorkOrder.Api --urls http://localhost:5080
