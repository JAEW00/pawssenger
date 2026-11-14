# PAWSSENGER 개발 환경 설치 스크립트 (관리자 PowerShell에서 실행)
# 실행: 우클릭 > 관리자 권한으로 실행 후
#   Set-ExecutionPolicy -Scope Process Bypass; .\setup-windows.ps1
$ErrorActionPreference = "Stop"

Write-Host "== 1. 가상화(BIOS) 확인 ==" -ForegroundColor Cyan
$virt = (Get-CimInstance Win32_Processor).VirtualizationFirmwareEnabled
$hyper = (Get-CimInstance Win32_ComputerSystem).HypervisorPresent
if ($hyper) {
  Write-Host "하이퍼바이저 실행 중 → BIOS 가상화 켜져 있음 OK"
} elseif ($virt -contains $false) {
  Write-Warning "BIOS에서 가상화(Intel VT-x / AMD SVM)가 꺼져 있습니다. BIOS에서 켠 뒤 다시 실행하세요."
} else { Write-Host "가상화 OK (또는 하이퍼바이저가 이미 실행 중)" }

Write-Host "== 2. Windows Hypervisor Platform 활성화 ==" -ForegroundColor Cyan
foreach ($f in @("HypervisorPlatform","VirtualMachinePlatform")) {
  $s = (Get-WindowsOptionalFeature -Online -FeatureName $f).State
  if ($s -ne "Enabled") { Enable-WindowsOptionalFeature -Online -FeatureName $f -All -NoRestart | Out-Null; Write-Host "$f 활성화 (재부팅 필요)" }
  else { Write-Host "$f 이미 활성화" }
}

Write-Host "== 3. 도구 설치 (winget) ==" -ForegroundColor Cyan
$pkgs = @("Git.Git","Google.AndroidStudio","GitHub.cli")
foreach ($p in $pkgs) {
  winget list --id $p -e | Out-Null
  if ($LASTEXITCODE -ne 0) { winget install --id $p -e --accept-package-agreements --accept-source-agreements }
  else { Write-Host "$p 이미 설치됨" }
}

Write-Host ""
Write-Host "완료. 재부팅 후 README_SETUP.md 의 '수동 단계'를 진행하세요." -ForegroundColor Green
