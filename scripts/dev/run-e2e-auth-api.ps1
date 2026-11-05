# The local API for the auth E2E runs (auth-email-access plan Task A16, PD-9). DEV ONLY.
#
# Starts Mailpit (SMTP on 127.0.0.1:1025, its API and inbox on 127.0.0.1:8025) and the API on profile dev with the phone
# channel of the argument. In legacy-login the end date is set one day ahead. Everything the API logs also goes to
# build/e2e-auth-api.log, where the phone suites read the codes of the `log` SMS sender. It prints the mode and the paths,
# never a secret.
#
# Needs: Docker, a local PostgreSQL and the env vars of .env.example in this shell (as for the plain dev run), the E2E
# seeds run against that database.
# Usage: .\scripts\dev\run-e2e-auth-api.ps1 [off|legacy-login|on]   (default off; Ctrl+C stops it and cleans up)
param(
    [ValidateSet('off', 'legacy-login', 'on')]
    [string]$Mode = 'off'
)
$ErrorActionPreference = 'Stop'

Set-Location (Join-Path $PSScriptRoot '..\..')
$mailpit = 'sunvibe-e2e-mailpit'
$mailpitImage = 'axllent/mailpit:v1.22.3'
$log = 'build/e2e-auth-api.log'

$env:AUTH_PHONE_MODE = $Mode
$env:SMS_PROVIDER = 'log'
$env:MAIL_HOST = 'localhost'
$env:MAIL_PORT = '1025'
$env:MAIL_SMTP_AUTH = 'false'
$env:MAIL_STARTTLS = 'false'
$env:MAIL_FROM = 'e2e@sunvibe.test'
if ($Mode -eq 'legacy-login') {
    $env:AUTH_PHONE_LEGACY_LOGIN_UNTIL = (Get-Date).ToUniversalTime().AddDays(1).ToString("yyyy-MM-ddTHH:mm:ssZ")
}

try {
    docker rm -f $mailpit 2>$null | Out-Null
    docker run -d --rm --name $mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 $mailpitImage | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Mailpit did not start' }

    New-Item -ItemType Directory -Path 'build' -Force | Out-Null
    Write-Host "auth e2e: profile dev, phone channel $Mode, Mailpit on 127.0.0.1:8025, log $log"
    & .\gradlew.bat :app:bootRun '--args=--spring.profiles.active=dev' 2>&1 | Tee-Object -FilePath $log
}
finally {
    docker rm -f $mailpit 2>$null | Out-Null
    Write-Host 'auth e2e: stopped; Mailpit removed'
}
