# Runs the same 19 checks as http/security-tests.http against a running server.
param(
    [string]$Host = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0

function Assert-Test {
    param([string]$Name, [bool]$Ok)
    if ($Ok) {
        Write-Host "[PASS] $Name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "[FAIL] $Name" -ForegroundColor Red
        $script:failed++
    }
}

function Invoke-Json {
    param(
        [string]$Method,
        [string]$Uri,
        [object]$Body = $null,
        [string]$Token = $null,
        [bool]$RawAuth = $false
    )
    $headers = @{}
    if ($Token) {
        if ($RawAuth) { $headers["Authorization"] = $Token }
        else { $headers["Authorization"] = "Bearer $Token" }
    }
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $Uri -Headers $headers -ContentType "application/json" -Body ($Body | ConvertTo-Json)
    }
    try {
        return Invoke-WebRequest -Method $Method -Uri $Uri -Headers $headers -UseBasicParsing
    } catch {
        return $_.Exception.Response
    }
}

Write-Host "Security API tests -> $Host"

# 1
$r = Invoke-Json GET "$Host/api/public/hello"
Assert-Test "1. public endpoint is open (200)" ($r.StatusCode -eq 200)

# 2
try { Invoke-RestMethod GET "$Host/api/user/me" } catch { $status = $_.Exception.Response.StatusCode.value__ }
Assert-Test "2. no token -> 401" ($status -eq 401)

# 3-5 login validation
try {
    Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"wrong-password"}'
} catch { $e3 = $_.ErrorDetails.Message | ConvertFrom-Json }
Assert-Test "3. wrong password -> 401" ($e3.error -eq "Invalid username or password")

try {
    Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body '{"username":"no_such_user","password":"whatever123"}'
} catch { $e4 = $_.ErrorDetails.Message | ConvertFrom-Json }
Assert-Test "4. unknown user -> same 401 message" ($e4.error -eq "Invalid username or password")

try {
    Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body '{"username":"","password":""}'
} catch { $e5 = $_.ErrorDetails.Message | ConvertFrom-Json }
Assert-Test "5. empty fields -> 400" ($null -ne $e5.fields.username)

$adminLogin = Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"Admin123!"}'
Assert-Test "6. admin login -> 200 with JWT" (
    $adminLogin.tokenType -eq "Bearer" -and ($adminLogin.token.Split(".").Count -eq 3) -and ($adminLogin.roles -contains "ROLE_ADMIN")
)

$userLogin = Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body '{"username":"user","password":"User123!"}'
Assert-Test "7. user login -> ROLE_USER only" (
    $userLogin.roles.Count -eq 1 -and $userLogin.roles[0] -eq "ROLE_USER"
)
$userParts = $userLogin.token.Split(".")
$adminParts = $adminLogin.token.Split(".")
$forgedToken = "$($userParts[0]).$($adminParts[1]).$($userParts[2])"

$meUser = Invoke-RestMethod GET "$Host/api/user/me" -Headers @{ Authorization = "Bearer $($userLogin.token)" }
Assert-Test "8. user token on /api/user/me -> 200" ($meUser.username -eq "user" -and $null -eq $meUser.password)

try { Invoke-RestMethod GET "$Host/api/admin/users" -Headers @{ Authorization = "Bearer $($userLogin.token)" } } catch { $s9 = $_.Exception.Response.StatusCode.value__ }
Assert-Test "9. user token on admin endpoint -> 403" ($s9 -eq 403)

$allUsers = Invoke-RestMethod GET "$Host/api/admin/users" -Headers @{ Authorization = "Bearer $($adminLogin.token)" }
$adminRow = $allUsers | Where-Object { $_.username -eq "admin" }
Assert-Test "10. admin lists users -> clean JSON" ($adminRow.roles.Count -eq 2)

$meAdmin = Invoke-RestMethod GET "$Host/api/user/me" -Headers @{ Authorization = "Bearer $($adminLogin.token)" }
Assert-Test "11. admin token on /api/user/me -> 200" ($meAdmin.username -eq "admin")

try { Invoke-RestMethod GET "$Host/api/admin/users" -Headers @{ Authorization = "Bearer $forgedToken" } } catch { $s12 = $_.Exception.Response.StatusCode.value__ }
Assert-Test "12. forged token -> 401" ($s12 -eq 401)

try { Invoke-RestMethod GET "$Host/api/user/me" -Headers @{ Authorization = "Bearer this.is.not-a-jwt" } } catch { $s13 = $_.Exception.Response.StatusCode.value__ }
Assert-Test "13. garbage token -> 401" ($s13 -eq 401)

try { Invoke-RestMethod GET "$Host/api/user/me" -Headers @{ Authorization = $userLogin.token } } catch { $s14 = $_.Exception.Response.StatusCode.value__ }
Assert-Test "14. missing Bearer prefix -> 401" ($s14 -eq 401)

$newName = "student_$(Get-Date -Format 'yyyyMMddHHmmss')"
$reg = Invoke-RestMethod POST "$Host/api/auth/register" -ContentType "application/json" -Body (@{ username = $newName; password = "Student123!" } | ConvertTo-Json)
Assert-Test "15. register -> 201 ROLE_USER" ($reg.roles.Count -eq 1 -and $reg.roles[0] -eq "ROLE_USER")

$regLogin = Invoke-RestMethod POST "$Host/api/auth/login" -ContentType "application/json" -Body (@{ username = $newName; password = "Student123!" } | ConvertTo-Json)
Assert-Test "16. new user can log in -> 200" ($null -ne $regLogin.token)

try {
    Invoke-RestMethod POST "$Host/api/auth/register" -ContentType "application/json" -Body '{"username":"admin","password":"TryToTakeOver1!"}'
} catch { $s17 = $_.Exception.Response.StatusCode.value__ }
Assert-Test "17. existing username -> 409" ($s17 -eq 409)

try {
    Invoke-RestMethod POST "$Host/api/auth/register" -ContentType "application/json" -Body (@{ username = "shorty_x"; password = "123" } | ConvertTo-Json)
} catch { $e18 = $_.ErrorDetails.Message | ConvertFrom-Json }
Assert-Test "18. short password -> 400" ($null -ne $e18.fields.password)

try {
    $hacker = Invoke-RestMethod POST "$Host/api/auth/register" -ContentType "application/json" -Body (@{ username = "hacker_x"; password = "Hacker123!"; roles = @("ROLE_ADMIN") } | ConvertTo-Json)
    $safe19 = ($hacker.roles -notcontains "ROLE_ADMIN")
} catch { $safe19 = $true }
Assert-Test "19. client cannot choose ROLE_ADMIN" ($safe19)

Write-Host "`n$passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
