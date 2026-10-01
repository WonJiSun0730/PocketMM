param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$LoginId
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($LoginId)) {
    $LoginId = Read-Host "PocketMM login ID"
}
$Password = Read-Host "PocketMM password" -AsSecureString
$Credential = New-Object System.Management.Automation.PSCredential($LoginId, $Password)
$PlainPassword = $Credential.GetNetworkCredential().Password

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Invoke-Json {
    param(
        [Parameter(Mandatory=$true)][string]$Method,
        [Parameter(Mandatory=$true)][string]$Path,
        $Body
    )
    $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        WebSession = $session
        ContentType = "application/json; charset=utf-8"
    }
    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 12)
    }
    try {
        Invoke-RestMethod @params
    } catch {
        Write-Host ""
        Write-Host "REQUEST FAILED: $Method $Path" -ForegroundColor Red
        if ($_.Exception.Response) {
            Write-Host "HTTP STATUS: $([int]$_.Exception.Response.StatusCode) $($_.Exception.Response.StatusDescription)" -ForegroundColor Red
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $responseBody = $reader.ReadToEnd()
                if (-not [string]::IsNullOrWhiteSpace($responseBody)) {
                    Write-Host "RESPONSE BODY: $responseBody" -ForegroundColor Red
                }
            } catch {
                # Keep the original HTTP exception as the primary failure.
            }
        }
        throw
    }
}

Write-Host "[1/10] Logging in..."
Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/auth/login" -WebSession $session -ContentType "application/x-www-form-urlencoded" -Body @{
    loginId = $LoginId
    password = $PlainPassword
} | Out-Null
$PlainPassword = $null

$me = Invoke-Json Get "/api/auth/me"
Write-Host "Authenticated as $($me.loginId)"

Write-Host "[2/10] Creating Victorian London world..."
$world = Invoke-Json Post "/api/worlds" @{
    name = "Victorian London"
    description = "Late-Victorian London: fog, gaslight, hansom cabs, newspapers, private clubs, police work, class tension, and mysteries hidden behind respectable facades."
}

Write-Host "[3/10] Creating world system..."
Invoke-Json Put "/api/worlds/$($world.id)/systems" @{
    playGuide = "Treat Victorian London as a living social world rather than a lore lecture. Preserve period atmosphere and social constraints, but prioritize natural character interaction, investigation, consequences, and player agency."
    systems = @(
        @{
            name = "Case Tension"
            changeCondition = "Increase when danger, suspicion, pursuit, confrontation, or a major clue raises pressure. Decrease when immediate danger passes, the group reaches safety, or uncertainty is substantially resolved."
            minValue = 0
            maxValue = 100
            initialValue = 20
            states = @(
                @{ minValue = 0; maxValue = 24; label = "Quiet" },
                @{ minValue = 25; maxValue = 49; label = "Uneasy" },
                @{ minValue = 50; maxValue = 74; label = "Dangerous" },
                @{ minValue = 75; maxValue = 100; label = "Critical" }
            )
        }
    )
} | Out-Null

Write-Host "[4/10] Creating Sherlock Holmes..."
$sherlock = Invoke-Json Post "/api/characters" @{
    name = "Sherlock Holmes"
    description = "A brilliant consulting detective in Victorian London. He observes minute details, reasons rapidly, and can be incisive or eccentric without becoming needlessly theatrical."
    rawDescription = "Registered Room Cast character for the Victorian London integration scenario."
    examples = @(
        "You have brought me a problem, not merely a letter.",
        "The mud on your cuff is more informative than your explanation."
    )
    exampleDialogue = 'Sherlock Holmes: "Come in. You have been standing in the fog long enough."'
    personality = "Analytical, intensely observant, self-possessed, intellectually restless, dryly witty, impatient with sloppy reasoning, but capable of focused concern."
    speechStyle = "Precise Victorian English rendered naturally in Korean. Controlled, concise, occasionally dry or cutting. Avoid constant grand deductions or catchphrases."
    background = "A consulting detective based at 221B Baker Street. Works with police and private clients while pursuing unusual cases independently."
    appearance = "Tall and lean, sharp-featured, keen-eyed, usually dressed neatly for late-Victorian London."
}

Write-Host "[5/10] Creating Victor Hale..."
$victor = Invoke-Json Post "/api/characters" @{
    name = "Victor Hale"
    description = "An independent investigator and former military surgeon who sometimes crosses paths with Holmes. Practical, socially smoother than Holmes, and willing to enter dangerous places personally."
    rawDescription = "Registered Room Cast character for the Victorian London integration scenario."
    examples = @(
        "Holmes, you can dissect the footprints after we make certain no one is bleeding upstairs.",
        "Miss Watson, I would rather hear what you noticed before anyone tells you what you were supposed to notice."
    )
    exampleDialogue = 'Victor Hale: "I came because the address was familiar. The blood on the envelope was not."'
    personality = "Pragmatic, steady under pressure, perceptive, quietly protective, skeptical of needless risk, with restrained humor."
    speechStyle = "Direct and composed, warmer and more conversational than Holmes. Period-appropriate without exaggerated archaic phrasing."
    background = "A former military surgeon now working independently around London. Familiar with injuries, criminal districts, and the practical side of investigations."
    appearance = "Broad-shouldered, well-kept, weathered by field experience, usually in a dark coat suitable for London streets."
}

Write-Host "[6/10] Linking characters to world..."
Invoke-Json Put "/api/worlds/$($world.id)/characters" @{
    characterIds = @($sherlock.id, $victor.id)
} | Out-Null

Write-Host "[7/10] Creating Room and cast..."
Write-Host "  [7.1] Creating Room..."
$room = Invoke-Json Post "/api/worlds/$($world.id)/rooms" @{
    title = "The Baker Street Case"
    visibility = "PRIVATE"
}
Write-Host "  [7.2] Adding Sherlock Holmes to Room Cast..."
Invoke-Json Post "/api/worlds/$($world.id)/rooms/$($room.id)/cast" @{ characterId = $sherlock.id } | Out-Null
Write-Host "  [7.3] Adding Victor Hale to Room Cast..."
Invoke-Json Post "/api/worlds/$($world.id)/rooms/$($room.id)/cast" @{ characterId = $victor.id } | Out-Null

Write-Host "[8/10] Creating Intro..."
$intro = Invoke-Json Post "/api/worlds/$($world.id)/rooms/$($room.id)/intros" @{
    title = "Fog over Baker Street"
    content = "A cold evening fog hangs over Baker Street. Eleanor Watson has arrived at 221B carrying a sealed letter delivered without a return address. A dark reddish stain marks one corner of the envelope. The windows above are lit, but no registered Room Cast character is considered physically present in Eleanor's current scene until the story brings them in."
    sortOrder = 1
}

Write-Host "[9/10] Preparing Eleanor Watson player profile..."
$profiles = @(Invoke-Json Get "/api/player-profiles")
$eleanor = $profiles | Where-Object { $_.name -eq "Eleanor Watson" } | Select-Object -First 1
$eleanorBody = @{
    name = "Eleanor Watson"
    description = "A perceptive young woman living in Victorian London. Curious and composed, but not omniscient; she notices details through ordinary observation and decides her own risks, emotions, conclusions, and commitments."
    avatar = $null
}
if ($null -ne $eleanor) {
    $eleanor = Invoke-Json Put "/api/player-profiles/$($eleanor.id)" $eleanorBody
} else {
    if ($profiles.Count -ge 10) {
        throw "All 10 PlayerProfile slots are occupied and no existing Eleanor Watson profile was found."
    }
    $eleanor = Invoke-Json Post "/api/player-profiles" $eleanorBody
}

Write-Host "[10/10] Starting Room session..."
$play = Invoke-Json Post "/api/worlds/$($world.id)/rooms/$($room.id)/sessions" @{
    playerProfileId = $eleanor.id
    introId = $intro.id
}

$castResponse = Invoke-Json Get "/api/worlds/$($world.id)/rooms/$($room.id)/sessions/$($play.id)/cast"
$systemResponse = Invoke-Json Get "/api/worlds/$($world.id)/rooms/$($room.id)/sessions/$($play.id)/systems"

$cast = @()
if ($null -ne $castResponse) {
    $cast = @($castResponse)
}
$systems = @()
if ($null -ne $systemResponse) {
    $systems = @($systemResponse)
}

Write-Host ""
Write-Host "================ VICTORIAN ROOM READY ================"
Write-Host "WORLD_ID      = $($world.id)"
Write-Host "SHERLOCK_ID   = $($sherlock.id)"
Write-Host "VICTOR_ID     = $($victor.id)"
Write-Host "ROOM_ID       = $($room.id)"
Write-Host "INTRO_ID      = $($intro.id)"
Write-Host "PLAYER_ID     = $($eleanor.id)"
Write-Host "SESSION_ID    = $($play.id)"
Write-Host "ACTIVE_CAST   = $($cast.Count) character(s)"
if ($cast.Count -gt 0) {
    Write-Host "ACTIVE_CAST_DETAIL:"
    $cast | ConvertTo-Json -Depth 6 | Write-Host
}
if ($systems.Count -gt 0) {
    Write-Host "CASE_TENSION  = $($systems[0].currentValue)"
}
Write-Host "======================================================"
Write-Host ""
Write-Host "Expected initial state: ACTIVE_CAST = 0, CASE_TENSION = 20"
