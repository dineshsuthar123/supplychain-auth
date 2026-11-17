param([Parameter(Mandatory=$true)][ValidateLength(1,500)][string]$Message)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$icon = New-Object System.Windows.Forms.NotifyIcon
try {
    $icon.Icon = [System.Drawing.SystemIcons]::Information
    $icon.Text = 'SupplyPrint Oracle capacity'
    $icon.Visible = $true
    $icon.ShowBalloonTip(10000, 'SupplyPrint Oracle A1 capacity', $Message, [System.Windows.Forms.ToolTipIcon]::Info)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    while ($timer.Elapsed.TotalSeconds -lt 12) {
        [System.Windows.Forms.Application]::DoEvents()
        Start-Sleep -Milliseconds 200
    }
} finally {
    $icon.Visible = $false
    $icon.Dispose()
}
