param(
    [string]$GatewayBaseUrl = "http://127.0.0.1:8080",
    [string]$ImagePath = "",
    [switch]$SkipProfileEdit
)

$ErrorActionPreference = "Stop"

function New-RequestId([string]$prefix) {
    return "$prefix-$([DateTimeOffset]::Now.ToUnixTimeMilliseconds())-$([Guid]::NewGuid().ToString('N').Substring(0, 8))"
}

function Invoke-BlueNoteJson {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body = $null,
        [string]$AccessToken = "",
        [hashtable]$Headers = @{}
    )

    $uri = "$GatewayBaseUrl$Path"
    $requestHeaders = @{
        "Content-Type" = "application/json"
        "X-Request-Id" = New-RequestId "smoke"
        "X-Device-Id" = $script:DeviceId
    }

    foreach ($item in $Headers.GetEnumerator()) {
        $requestHeaders[$item.Key] = $item.Value
    }

    if ($AccessToken) {
        $requestHeaders["Authorization"] = "Bearer $AccessToken"
    }

    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $requestHeaders
        TimeoutSec = 30
    }

    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
    }

    $response = Invoke-RestMethod @params
    if ($null -eq $response.code) {
        throw "Unexpected response from $Method ${Path}: missing code"
    }
    if ([int]$response.code -ne 0) {
        $message = $response | ConvertTo-Json -Depth 20
        throw "API failed: $Method $Path -> $message"
    }
    if (-not $response.traceId) {
        throw "API failed: $Method $Path -> missing traceId"
    }
    return $response
}

function Assert-Field {
    param(
        [object]$Object,
        [string]$Field,
        [string]$Context
    )
    if ($null -eq $Object.$Field -or [string]::IsNullOrWhiteSpace([string]$Object.$Field)) {
        throw "$Context missing required field: $Field"
    }
}

function New-SmokeImage {
    param([string]$Path)

    if ($Path -and (Test-Path -LiteralPath $Path)) {
        return (Resolve-Path -LiteralPath $Path).Path
    }

    $defaultPath = Join-Path $PSScriptRoot "..\out\main-chain-smoke.png"
    $resolvedDefaultPath = [IO.Path]::GetFullPath($defaultPath)
    $dir = Split-Path -Parent $resolvedDefaultPath
    if (-not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir | Out-Null
    }

    $pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    [IO.File]::WriteAllBytes($resolvedDefaultPath, [Convert]::FromBase64String($pngBase64))
    return $resolvedDefaultPath
}

function Invoke-PresignedPut {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [string]$File
    )

    $bytes = [IO.File]::ReadAllBytes($File)
    $request = [System.Net.HttpWebRequest]::Create($Uri)
    $request.Method = "PUT"
    $request.ContentLength = $bytes.Length
    $request.Timeout = 30000
    $request.ReadWriteTimeout = 30000

    foreach ($item in $Headers.GetEnumerator()) {
        if ($item.Key -eq "Content-Type") {
            $request.ContentType = [string]$item.Value
        } elseif ($item.Key -ne "Content-Length" -and $item.Key -ne "Host") {
            $request.Headers[$item.Key] = [string]$item.Value
        }
    }

    $stream = $request.GetRequestStream()
    try {
        $stream.Write($bytes, 0, $bytes.Length)
    } finally {
        $stream.Dispose()
    }

    try {
        $response = $request.GetResponse()
        $response.Dispose()
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        $body = ""
        if ($response) {
            $reader = New-Object IO.StreamReader($response.GetResponseStream())
            try {
                $body = $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
                $response.Dispose()
            }
        }
        throw "Presigned PUT failed: $($_.Exception.Message) $body"
    }
}

function Upload-SmokeImage {
    param(
        [string]$Scene,
        [string]$Filename,
        [string]$AccessToken,
        [string]$ImageFile
    )

    $fileSize = (Get-Item -LiteralPath $ImageFile).Length
    $tokenResponse = Invoke-BlueNoteJson -Method "POST" -Path "/api/files/upload-token" -AccessToken $AccessToken -Body @{
        scene = $Scene
        filename = $Filename
        mimeType = "image/png"
        fileSize = $fileSize
    }
    $token = $tokenResponse.data
    Assert-Field $token "fileId" "UploadToken"
    Assert-Field $token "uploadUrl" "UploadToken"
    Assert-Field $token "uploadMethod" "UploadToken"
    if ($token.uploadMethod -ne "PRESIGNED_PUT") {
        throw "UploadToken uploadMethod expected PRESIGNED_PUT but got $($token.uploadMethod)"
    }

    $putHeaders = @{}
    if ($token.headers) {
        foreach ($prop in $token.headers.PSObject.Properties) {
            $putHeaders[$prop.Name] = [string]$prop.Value
        }
    }
    if (-not $putHeaders.ContainsKey("Content-Type")) {
        $putHeaders["Content-Type"] = "image/png"
    }

    Invoke-PresignedPut -Uri $token.uploadUrl -Headers $putHeaders -File $ImageFile

    $confirmResponse = Invoke-BlueNoteJson -Method "POST" -Path "/api/files/$($token.fileId)/confirm" -AccessToken $AccessToken -Body @{
        etag = ""
        fileSize = $fileSize
    }
    $confirm = $confirmResponse.data
    if ($confirm.fileStatus -ne "UPLOADED" -and $confirm.fileStatus -ne "BOUND") {
        throw "ConfirmUpload fileStatus expected UPLOADED or BOUND but got $($confirm.fileStatus)"
    }
    if ($confirm.scene -ne $Scene) {
        throw "ConfirmUpload scene expected $Scene but got $($confirm.scene)"
    }
    Assert-Field $confirm "accessUrl" "ConfirmUpload"
    return $confirm
}

$GatewayBaseUrl = $GatewayBaseUrl.TrimEnd("/")
$script:DeviceId = "h5-smoke-$([Guid]::NewGuid().ToString('N').Substring(0, 12))"
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$username = "main_chain_$stamp"
$password = "Password123!"
$imageFile = New-SmokeImage -Path $ImagePath

$registerResponse = Invoke-BlueNoteJson -Method "POST" -Path "/api/auth/register" -Body @{
    username = $username
    password = $password
    deviceId = $script:DeviceId
    deviceName = "Codex H5 Smoke"
    platform = "H5"
    appVersion = "0.1.0"
}
$token = $registerResponse.data
Assert-Field $token "userId" "TokenPair"
Assert-Field $token "accessToken" "TokenPair"
Assert-Field $token "refreshToken" "TokenPair"

$accessToken = [string]$token.accessToken
$userId = [string]$token.userId

$meResponse = Invoke-BlueNoteJson -Method "GET" -Path "/api/users/me" -AccessToken $accessToken
$me = $meResponse.data
if ([string]$me.userId -ne $userId) {
    throw "GET /api/users/me userId mismatch: expected $userId but got $($me.userId)"
}
Assert-Field $me "bluenoteNo" "UserProfile"
Assert-Field $me "nickname" "UserProfile"
Assert-Field $me "userStatus" "UserProfile"

$noteImage = Upload-SmokeImage -Scene "NOTE_IMAGE" -Filename "main-chain-note.png" -AccessToken $accessToken -ImageFile $imageFile

$idempotencyKey = "$($script:DeviceId):publish:$([Guid]::NewGuid().ToString('N'))"
$publishResponse = Invoke-BlueNoteJson -Method "POST" -Path "/api/notes" -AccessToken $accessToken -Headers @{
    "Idempotency-Key" = $idempotencyKey
} -Body @{
    clientRequestId = $idempotencyKey
    title = "Main chain smoke $stamp"
    content = "Smoke verification for register, upload, publish and detail."
    visibility = "PUBLIC"
    commentEnabled = $true
    mediaFiles = @(
        @{
            fileId = $noteImage.fileId
            mediaType = "IMAGE"
            sortOrder = 1
            cover = $true
        }
    )
    topics = @("smoke", "main-chain")
}
$published = $publishResponse.data
Assert-Field $published "noteId" "PublishNoteResponse"
if ($published.noteStatus -ne "PUBLISHED") {
    throw "PublishNote noteStatus expected PUBLISHED but got $($published.noteStatus)"
}
if ($published.visibility -ne "PUBLIC") {
    throw "PublishNote visibility expected PUBLIC but got $($published.visibility)"
}

$noteId = [string]$published.noteId
$detailResponse = Invoke-BlueNoteJson -Method "GET" -Path "/api/notes/$noteId" -AccessToken $accessToken
$detail = $detailResponse.data
if ([string]$detail.noteId -ne $noteId) {
    throw "NoteDetail noteId mismatch: expected $noteId but got $($detail.noteId)"
}
if ($detail.noteStatus -ne "PUBLISHED") {
    throw "NoteDetail noteStatus expected PUBLISHED but got $($detail.noteStatus)"
}
if (-not $detail.mediaFiles -or $detail.mediaFiles.Count -lt 1) {
    throw "NoteDetail missing mediaFiles"
}
if ($null -eq $detail.counts) {
    throw "NoteDetail missing counts"
}
if ($null -eq $detail.viewerAction) {
    throw "NoteDetail missing viewerAction"
}

$avatarFileId = $null
$coverFileId = $null
$profileVersionAfter = $null

if (-not $SkipProfileEdit) {
    $avatar = Upload-SmokeImage -Scene "USER_AVATAR" -Filename "main-chain-avatar.png" -AccessToken $accessToken -ImageFile $imageFile
    $cover = Upload-SmokeImage -Scene "USER_HOME_COVER" -Filename "main-chain-cover.png" -AccessToken $accessToken -ImageFile $imageFile
    $avatarFileId = [string]$avatar.fileId
    $coverFileId = [string]$cover.fileId

    $profileResponse = Invoke-BlueNoteJson -Method "PUT" -Path "/api/users/me/profile" -AccessToken $accessToken -Body @{
        nickname = "Smoke User $stamp"
        avatarFileId = $avatarFileId
        bio = "Main chain smoke profile"
        gender = "UNKNOWN"
        birthday = $null
        regionCode = "CN-330100"
        homeCoverFileId = $coverFileId
        baseProfileVersion = $me.profileVersion
    }
    $profile = $profileResponse.data
    if ([string]$profile.userId -ne $userId) {
        throw "UpdateProfile userId mismatch: expected $userId but got $($profile.userId)"
    }
    $profileVersionAfter = $profile.profileVersion

    $meAfter = (Invoke-BlueNoteJson -Method "GET" -Path "/api/users/me" -AccessToken $accessToken).data
    if ([string]$meAfter.avatarFileId -ne $avatarFileId) {
        throw "GET /api/users/me avatarFileId mismatch after profile edit"
    }
    if ([string]$meAfter.homeCoverFileId -ne $coverFileId) {
        throw "GET /api/users/me homeCoverFileId mismatch after profile edit"
    }
    Assert-Field $meAfter "avatarUrl" "UserProfileAfter"
    Assert-Field $meAfter "homeCoverUrl" "UserProfileAfter"
}

$userHome = (Invoke-BlueNoteJson -Method "GET" -Path "/api/users/$userId/home" -AccessToken $accessToken).data
if ([string]$userHome.user.userId -ne $userId) {
    throw "UserHome userId mismatch: expected $userId but got $($userHome.user.userId)"
}
if ($null -eq $userHome.counts) {
    throw "UserHome missing counts"
}

[pscustomobject]@{
    ok = $true
    gateway = $GatewayBaseUrl
    username = $username
    userId = $userId
    noteId = $noteId
    noteImageFileId = $noteImage.fileId
    avatarFileId = $avatarFileId
    homeCoverFileId = $coverFileId
    profileVersionBefore = $me.profileVersion
    profileVersionAfter = $profileVersionAfter
    homeDegraded = $userHome.degraded
    noteDegraded = $detail.degraded
} | ConvertTo-Json -Depth 20
