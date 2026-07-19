@echo off
powershell.exe -NoExit -ExecutionPolicy Bypass -Command ". '%~dp0use-local-toolchains.ps1'; Set-Location '%~dp0..'"
