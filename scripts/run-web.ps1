$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

dotnet run --project src/LAMWorkOrder.Web --urls http://localhost:5081
