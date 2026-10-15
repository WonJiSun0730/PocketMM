param(
    [ValidateSet('init','run','pause','resume','stop','status','delete')][string]$Action='status',
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]{1,60}$')][string]$RunId,
    [ValidateSet('Smoke','Short','Medium','Long','Custom')][string]$Preset='Smoke',
    [int]$Turns=20,
    [string]$Maven='mvn.cmd',
    [string]$MavenCache='',
    [string]$ApprovePaidRun='',
    [string]$Reconciliation='',
    [string]$Scenario='',
    [switch]$RecoverCommitted,
    [int]$Steps=100000
)
$ErrorActionPreference='Stop'
$qaRoot=Split-Path $PSScriptRoot -Parent
$runRoot=[IO.Path]::GetFullPath((Join-Path $qaRoot 'target/pocket-qa'))
$runPath=[IO.Path]::GetFullPath((Join-Path $runRoot $RunId))
if (-not $runPath.StartsWith($runRoot+[IO.Path]::DirectorySeparatorChar)) { throw 'Run path escaped QA directory' }
$configPath=Join-Path $runPath 'config.json'
switch($Action) {
    'init' {
        if(Test-Path -LiteralPath $configPath) { throw 'Run configuration exists; choose a new run ID' }
        $length=@{Smoke=20;Short=100;Medium=500;Long=1000}
        if($Preset -ne 'Custom') {$Turns=$length[$Preset]}
        if($Turns -lt 1 -or $Turns -gt 100000) {throw 'Invalid turn count'}
        New-Item -ItemType Directory -Path $runPath -Force | Out-Null
        $config=@{runId=$RunId;turns=$Turns;paid=$false;approval=$null;persona=@{kind='MIXED';personality='차분하고 호기심 많음';speech='자연스러운 한국어';relationship='새로 알게 된 동료';tendencies='답변에 반응하고 화제를 섞음';goal='함께 시간을 보내며 신뢰 쌓기'};personaModel='mock-persona';targetModel='mock-target';extractionModel='mock-extraction';budgetUsd=0;turnReservationUsd=0;prices=@{};recentTurns=3;memoryEnabled=$true}
        [IO.File]::WriteAllText($configPath,($config | ConvertTo-Json -Depth 8),[Text.UTF8Encoding]::new($false))
        Write-Output "Created FREE MOCK configuration: $configPath"
    }
    'run' {
        $config=Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
        if($config.paid -and $ApprovePaidRun -ne $RunId) {throw 'Paid execution needs -ApprovePaidRun matching RunId and a configured budget/pricing table'}
        Write-Output "Mode=$($config.paid) paid; turns=$($config.turns); reservation estimate=$([decimal]$config.turnReservationUsd * $config.turns) USD; budget=$($config.budgetUsd) USD"
        $arguments=@('-o',"-Dpocket.qa.execute=true","-Dpocket.qa.config=$configPath","-Dpocket.qa.run-id=$RunId","-Dpocket.qa.steps=$Steps",'-Dtest=PersonaQaExecutionTest','test')
        if($ApprovePaidRun) {$arguments+="-Dpocket.qa.approve=$ApprovePaidRun"}
        if($Reconciliation) {$arguments+="-Dpocket.qa.reconcile=$([IO.Path]::GetFullPath($Reconciliation))"}
        if($Scenario) {$arguments+="-Dpocket.qa.scenario=$([IO.Path]::GetFullPath($Scenario))"}
        if($RecoverCommitted) {$arguments+='-Dpocket.qa.recover=true'}
        if($MavenCache) {$arguments+="-Dmaven.repo.local=$MavenCache"}
        New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
        $handle=[IO.File]::Open((Join-Path $runRoot ($RunId+'.process.lock')),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        Push-Location $qaRoot
        try { & $Maven @arguments; if($LASTEXITCODE -ne 0) {throw "QA process failed: $LASTEXITCODE"} } finally {Pop-Location; $handle.Dispose()}
    }
    'status' {
        $state=Get-Content -LiteralPath (Join-Path $runPath 'run.json') -Raw | ConvertFrom-Json
        [pscustomobject]@{Run=$RunId;Status=$state.status;Completed=$state.turns.Count;Total=$state.spec.turns;Stage=$state.stage;Reason=$state.reason;Report=(Join-Path $runPath 'report.md')}
    }
    'delete' {
        # Run-level process lock prevents deleting an active database/checkpoint.
        $processLock=Join-Path $runRoot ($RunId+'.process.lock')
        $handle=[IO.File]::Open($processLock,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        try { Remove-Item -LiteralPath $runPath -Recurse -Force } finally { $handle.Dispose() }
    }
    default {
        $control=Join-Path $runPath 'control'
        $temporary=Join-Path $runPath ('control-'+[guid]::NewGuid().ToString('N')+'.tmp')
        [IO.File]::WriteAllText($temporary,$Action.ToUpperInvariant(),[Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporary -Destination $control -Force
        Write-Output "$Action requested; applied between turns. Resume also needs a run command if no runner is active."
    }
}
