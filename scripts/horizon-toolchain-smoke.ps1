# This script is intentionally CI-only: it downloads and executes native build tools.
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or !$env:RUNNER_TEMP) {
	throw 'Run this smoke check on GitHub Actions, not on a local workstation.'
}

$repository = Split-Path -Parent $PSScriptRoot
$pinPath = Join-Path $repository 'packages/app-lib/src/horizon/mingw.json'
$pin = Get-Content -LiteralPath $pinPath -Raw | ConvertFrom-Json
if ($pin.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
	$pin.version -notmatch '^\d+\.\d+\.\d+$' -or
	$pin.rust_version -notmatch '^\d+\.\d+\.\d+$' -or
	$pin.toolchain_directory -ne "$($pin.rust_version)-x86_64-pc-windows-gnu" -or
	!$pin.url.StartsWith('https://github.com/brechtsanders/winlibs_mingw/releases/download/')) {
	throw 'Invalid managed toolchain pin.'
}

$work = Join-Path $env:RUNNER_TEMP 'horizon-toolchain-smoke'
$evidence = Join-Path $work 'evidence'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
Copy-Item -LiteralPath $pinPath -Destination (Join-Path $evidence 'mingw.json')
Start-Transcript -Path (Join-Path $evidence 'smoke.log') | Out-Null

function Invoke-Checked {
	param([string]$Program, [string[]]$Arguments)
	Write-Host "> $Program $($Arguments -join ' ')"
	& $Program @Arguments
	if ($LASTEXITCODE -ne 0) { throw "$Program failed with exit code $LASTEXITCODE" }
}

try {
	$archive = Join-Path $work 'mingw.zip'
	Invoke-WebRequest -Uri $pin.url -OutFile $archive -MaximumRetryCount 3 -RetryIntervalSec 5
	$actualHash = (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant()
	if ($actualHash -ne $pin.sha256.ToLowerInvariant()) { throw 'MinGW SHA-256 mismatch.' }
	Write-Host "Verified compiler archive SHA-256: $actualHash"
	Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $work 'compiler')
	$bin = Join-Path $work 'compiler/mingw64/bin'
	$gcc = Join-Path $bin 'gcc.exe'
	$gxx = Join-Path $bin 'g++.exe'
	$ar = Join-Path $bin 'ar.exe'
	foreach ($tool in @($gcc, $gxx, $ar)) {
		if (!(Test-Path -LiteralPath $tool -PathType Leaf)) { throw "Compiler tool missing: $tool" }
	}
	$env:PATH = "$bin;$env:PATH"
	Invoke-Checked $gcc @('--version')
	$version = & $gcc -dumpfullversion
	if ($LASTEXITCODE -ne 0 -or $version.Trim() -ne $pin.version) { throw 'Unexpected GCC version.' }
	$machine = & $gcc -dumpmachine
	if ($LASTEXITCODE -ne 0 -or $machine.Trim() -ne 'x86_64-w64-mingw32') { throw 'Unexpected GCC target.' }
	Invoke-Checked $gxx @('--version')
	Invoke-Checked $ar @('--version')

	$cSource = Join-Path $work 'windows-smoke.c'
	@"
#include <windows.h>
int main(void) {
    HANDLE event = CreateEventW(NULL, FALSE, FALSE, NULL);
    if (event == NULL || GetCurrentProcessId() == 0) return 1;
    return CloseHandle(event) ? 0 : 2;
}
"@ | Set-Content -LiteralPath $cSource -Encoding utf8
	$cppSource = Join-Path $work 'windows-smoke.cpp'
	@"
#include <windows.h>
#include <vector>
#include <thread>
int main() {
    std::vector<unsigned long> ids;
    std::thread worker([&] { ids.push_back(GetCurrentProcessId()); });
    worker.join();
    return ids.size() == 1 && ids[0] != 0 ? 0 : 1;
}
"@ | Set-Content -LiteralPath $cppSource -Encoding utf8
	foreach ($optimization in @('0', '2', '3')) {
		$cOutput = Join-Path $work "windows-c-O$optimization.exe"
		$cppOutput = Join-Path $work "windows-cpp-O$optimization.exe"
		Invoke-Checked $gcc @("-O$optimization", '-Wall', '-Werror', $cSource, '-o', $cOutput)
		Invoke-Checked $cOutput @()
		Invoke-Checked $gxx @("-O$optimization", '-std=c++17', '-Wall', '-Werror', $cppSource, '-o', $cppOutput)
		Invoke-Checked $cppOutput @()
	}

	# Match Horizon's GNU Rust release and use the pinned compiler explicitly,
	# so a runner's preinstalled MSYS2/MinGW cannot accidentally make this pass.
	$toolchain = $pin.toolchain_directory
	Invoke-Checked 'rustup' @('toolchain', 'install', $toolchain, '--profile', 'minimal', '--no-self-update')
	$env:CC_x86_64_pc_windows_gnu = $gcc
	$env:CXX_x86_64_pc_windows_gnu = $gxx
	$env:AR_x86_64_pc_windows_gnu = $ar
	$env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER = $gcc
	$env:CC_ENABLE_DEBUG_OUTPUT = '1'
	$env:CARGO_BUILD_JOBS = '2'
	$env:CARGO_HOME = Join-Path $work 'cargo-home'
	$env:CARGO_TARGET_DIR = Join-Path $work 'target'
	$env:TMP = Join-Path $work 'tmp'
	$env:TEMP = $env:TMP
	$env:TMPDIR = $env:TMP
	New-Item -ItemType Directory -Path $env:TMP -Force | Out-Null
	$crate = Join-Path $work 'native-dependency-smoke'
	New-Item -ItemType Directory -Path (Join-Path $crate 'src') -Force | Out-Null
	@"
[package]
name = "blockera-horizon-native-smoke"
version = "0.0.0"
edition = "2021"

[workspace]

[dependencies]
hidapi = "=2.6.7"
mimalloc = "=0.1.52"
libmimalloc-sys = "=0.1.49"
"@ | Set-Content -LiteralPath (Join-Path $crate 'Cargo.toml') -Encoding utf8
	@"
#[global_allocator]
static ALLOCATOR: mimalloc::MiMalloc = mimalloc::MiMalloc;
fn main() {
    let values: Vec<u64> = (0..10_000).collect();
    assert_eq!(values.iter().sum::<u64>(), 49_995_000);
    let api = hidapi::HidApi::new().expect("Windows HID initialization failed");
    println!("Native allocator and Windows HID OK ({} devices)", api.device_list().count());
}
"@ | Set-Content -LiteralPath (Join-Path $crate 'src/main.rs') -Encoding utf8
	$manifest = Join-Path $crate 'Cargo.toml'
	Invoke-Checked 'rustup' @('run', $toolchain, 'rustc', '-vV')
	Invoke-Checked 'rustup' @('run', $toolchain, 'cargo', 'generate-lockfile', '--manifest-path', $manifest)
	Copy-Item -LiteralPath (Join-Path $crate 'Cargo.lock') -Destination (Join-Path $evidence 'Cargo.lock')
	Invoke-Checked 'rustup' @('run', $toolchain, 'cargo', 'build', '--locked', '--release', '--target', 'x86_64-pc-windows-gnu', '--manifest-path', $manifest, '-vv')
	$program = Join-Path $env:CARGO_TARGET_DIR 'x86_64-pc-windows-gnu/release/blockera-horizon-native-smoke.exe'
	Invoke-Checked $program @()
	Write-Host 'PASS: pinned GCC C/C++ -O0/-O2/-O3 and Rust GNU hidapi/mimalloc build, link and runtime.'
} finally {
	Stop-Transcript | Out-Null
}
