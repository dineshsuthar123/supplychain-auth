param(
    [ValidateSet('Install','Status','Run','Disable','Enable','Remove')][string]$Action='Status',
    [string]$PythonPath='',
    [string]$ConfigPath=(Join-Path $env:USERPROFILE '.oci\config'),
    [ValidatePattern('^[A-Za-z0-9_-]{1,64}$')][string]$OciProfile='DEFAULT'
)
$ErrorActionPreference = 'Stop'
$taskName = 'SupplyPrint-Oracle-A1-Capacity'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$watcher = Join-Path $PSScriptRoot 'capacity-watch.py'
$stateDir = [IO.Path]::GetFullPath((Join-Path $env:LOCALAPPDATA 'SupplyPrint\OracleCapacity'))
$user = [Security.Principal.WindowsIdentity]::GetCurrent()
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing) {
    $ownerSid = if ($existing.Principal.UserId.StartsWith('S-1-')) {
        $existing.Principal.UserId
    } else {
        (New-Object Security.Principal.NTAccount($existing.Principal.UserId)).Translate([Security.Principal.SecurityIdentifier]).Value
    }
    if ($existing.Actions.Count -ne 1 -or !$existing.Actions[0].Arguments.Contains('"' + $watcher + '"') -or
        $ownerSid -ne $user.User.Value) {
        throw 'A different task owns this name. It was not changed.'
    }
}
if ($Action -eq 'Install') {
    if (!$PythonPath) { $PythonPath = Join-Path $repoRoot 'reports\oracle\oci-venv\Scripts\pythonw.exe' }
    $python = (Resolve-Path -LiteralPath $PythonPath).Path
    $config = (Resolve-Path -LiteralPath $ConfigPath).Path
    foreach ($value in @($python,$watcher,$config,$stateDir)) {
        if ($value -match '["\r\n]') { throw 'Invalid task path.' }
    }
    if (!(Test-Path -LiteralPath $watcher -PathType Leaf)) { throw 'Read-only watcher missing.' }
    # ACL applies only to this dedicated directory. Credentials are not copied here.
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
    & icacls.exe $stateDir /inheritance:r /grant:r ($user.Name + ':(OI)(CI)(F)') /grant:r 'SYSTEM:(OI)(CI)(F)' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Could not restrict private monitor-state permissions.' }
    $arguments = '"' + $watcher + '" --config "' + $config + '" --profile ' + $OciProfile + ' --state-dir "' + $stateDir + '"'
    $taskAction = New-ScheduledTaskAction -Execute $python -Argument $arguments -WorkingDirectory $repoRoot
    $trigger = New-ScheduledTaskTrigger -Once -At ([DateTime]::Now.AddHours(3)) -RepetitionInterval (New-TimeSpan -Hours 3)
    $principal = New-ScheduledTaskPrincipal -UserId $user.Name -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 5) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $trigger -Principal $principal -Settings $settings -Description 'SupplyPrint read-only OCI A1 capacity check every 3 hours. Local notification only. Never provisions or changes account/VM/network.' -Force | Out-Null
} elseif ($Action -eq 'Remove') {
    if ($existing) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
    Write-Output 'Capacity task removed; cloud resources and credentials were not changed.'
    exit
} elseif ($Action -eq 'Disable') {
    if ($existing) { Disable-ScheduledTask -TaskName $taskName | Out-Null }
} elseif ($Action -eq 'Enable') {
    if ($existing) { Enable-ScheduledTask -TaskName $taskName | Out-Null }
} elseif ($Action -eq 'Run') {
    if (!$existing) { throw 'Install the optional task first.' }
    Start-ScheduledTask -TaskName $taskName
}
$task = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if (!$task) { Write-Output 'Capacity task is not installed.'; exit }
$info = Get-ScheduledTaskInfo -TaskName $taskName
[pscustomobject]@{
    Task=$taskName; State=$task.State; Interval='3 hours'; LogonType=$task.Principal.LogonType;
    RunLevel=$task.Principal.RunLevel; NextRunIST=([TimeZoneInfo]::ConvertTimeBySystemTimeZoneId($info.NextRunTime,'India Standard Time')).ToString('yyyy-MM-dd HH:mm:ss');
    LastResult=$info.LastTaskResult; PrivateStatus=(Join-Path $stateDir 'monitor-state.json')
} | Format-List
