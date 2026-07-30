$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$api = Start-Process -FilePath "dotnet" -ArgumentList @(
  "run",
  "--project",
  "src/LAMWorkOrder.Api",
  "--urls",
  "http://localhost:5080"
) -PassThru -WindowStyle Hidden

try {
  dotnet run --project src/LAMWorkOrder.Web --urls http://localhost:5081
}
finally {
  if (!$api.HasExited) {
    Stop-Process -Id $api.Id -Force
  }
}
