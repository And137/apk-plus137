# apk-plus137: keep the taskbar pin of Mesh+ 137 across updates on Windows.
#
# jpackage's MSI removes the previous version before it installs the new one (RemoveExistingProducts before
# CostInitialize). That deletes the Start menu shortcut for a moment, and Windows drops the taskbar pin pointing at it.
# This script moves RemoveExistingProducts to the end of the install, just before InstallFinalize, so the new files and
# shortcuts go in first and the old version is removed afterwards. Shortcut components keep the same GUID in every
# build (jpackage derives them from the path), so the shortcuts stay. REINSTALLMODE=amus makes the new package
# overwrite every file even when its name and date match the old one, so no stale jar survives the upgrade.
#
# The .exe installer is a small wrapper with its own MSI stored as an RCDATA resource; that MSI is patched the same way.
#
# Usage: scripts/windows-installer-keep-pins.ps1 <directory with the .msi and .exe>
param([Parameter(Mandatory = $true)][string]$Dir)
$ErrorActionPreference = 'Stop'

function Invoke-Com($obj, [string]$name, [string]$kind, $argList) {
    $obj.GetType().InvokeMember($name, $kind, $null, $obj, $argList)
}

function Invoke-MsiSql($db, [string]$sql) {
    $view = Invoke-Com $db 'OpenView' 'InvokeMethod' @($sql)
    Invoke-Com $view 'Execute' 'InvokeMethod' $null | Out-Null
    $rows = @()
    if ($sql.TrimStart().StartsWith('SELECT')) {
        while ($rec = Invoke-Com $view 'Fetch' 'InvokeMethod' $null) {
            $rows += , (Invoke-Com $rec 'StringData' 'GetProperty' @(1))
        }
    }
    Invoke-Com $view 'Close' 'InvokeMethod' $null | Out-Null
    return $rows
}

function Update-Msi([string]$msi) {
    $installer = New-Object -ComObject WindowsInstaller.Installer
    $db = Invoke-Com $installer 'OpenDatabase' 'InvokeMethod' @($msi, 1) # msiOpenDatabaseModeTransact
    $finalize = @(Invoke-MsiSql $db "SELECT Sequence FROM InstallExecuteSequence WHERE Action='InstallFinalize'")
    if ($finalize.Count -ne 1) { throw "${msi} has no InstallFinalize" }
    $seq = [int]$finalize[0] - 1
    Invoke-MsiSql $db "UPDATE InstallExecuteSequence SET Sequence=$seq WHERE Action='RemoveExistingProducts'" | Out-Null
    if (@(Invoke-MsiSql $db "SELECT Property FROM Property WHERE Property='REINSTALLMODE'").Count -eq 0) {
        Invoke-MsiSql $db "INSERT INTO Property (Property, Value) VALUES ('REINSTALLMODE', 'amus')" | Out-Null
    } else {
        Invoke-MsiSql $db "UPDATE Property SET Value='amus' WHERE Property='REINSTALLMODE'" | Out-Null
    }
    Invoke-Com $db 'Commit' 'InvokeMethod' $null | Out-Null
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($db) | Out-Null
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($installer) | Out-Null
    [GC]::Collect(); [GC]::WaitForPendingFinalizers()

    $db = Invoke-Com (New-Object -ComObject WindowsInstaller.Installer) 'OpenDatabase' 'InvokeMethod' @($msi, 0)
    $now = @(Invoke-MsiSql $db "SELECT Sequence FROM InstallExecuteSequence WHERE Action='RemoveExistingProducts'")
    if ($now.Count -ne 1 -or [int]$now[0] -ne $seq) { throw "${msi}: RemoveExistingProducts not moved" }
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($db) | Out-Null
    [GC]::Collect(); [GC]::WaitForPendingFinalizers()
    Write-Host "$msi`: RemoveExistingProducts -> $seq, REINSTALLMODE=amus"
}

Add-Type -TypeDefinition @"
using System;
using System.IO;
using System.Runtime.InteropServices;
public static class MsiInExe {
    const uint RT_RCDATA = 10;
    delegate bool NameProc(IntPtr h, IntPtr type, IntPtr name, IntPtr p);
    [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern IntPtr LoadLibraryExW(string f, IntPtr h, uint flags);
    [DllImport("kernel32")] static extern bool FreeLibrary(IntPtr h);
    [DllImport("kernel32", CharSet = CharSet.Unicode)]
    static extern bool EnumResourceNamesW(IntPtr h, IntPtr type, NameProc cb, IntPtr p);
    [DllImport("kernel32", CharSet = CharSet.Unicode)] static extern IntPtr FindResourceW(IntPtr h, IntPtr name, IntPtr type);
    [DllImport("kernel32")] static extern uint SizeofResource(IntPtr h, IntPtr r);
    [DllImport("kernel32")] static extern IntPtr LoadResource(IntPtr h, IntPtr r);
    [DllImport("kernel32")] static extern IntPtr LockResource(IntPtr r);
    [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern IntPtr BeginUpdateResourceW(string f, bool deleteExisting);
    [DllImport("kernel32", SetLastError = true, CharSet = CharSet.Unicode)]
    static extern bool UpdateResourceW(IntPtr h, IntPtr type, string name, ushort lang, byte[] data, uint size);
    [DllImport("kernel32", SetLastError = true)] static extern bool EndUpdateResourceW(IntPtr h, bool discard);
    [DllImport("kernel32")] static extern bool EnumResourceLanguagesW(IntPtr h, IntPtr type, string name, LangProc cb, IntPtr p);
    delegate bool LangProc(IntPtr h, IntPtr type, IntPtr name, ushort lang, IntPtr p);

    static readonly byte[] OleMagic = { 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1 };

    // Name and language of the RCDATA resource that holds an MSI (an OLE compound file); null when none.
    static Tuple<string, ushort> FindMsi(string exe) {
        IntPtr mod = LoadLibraryExW(exe, IntPtr.Zero, 0x2); // LOAD_LIBRARY_AS_DATAFILE
        if (mod == IntPtr.Zero) throw new IOException("cannot open " + exe);
        string found = null;
        ushort lang = 0;
        try {
            EnumResourceNamesW(mod, (IntPtr)RT_RCDATA, (h, t, n, p) => {
                if (((long)n >> 16) == 0) return true; // integer ids: not the MSI
                IntPtr res = FindResourceW(h, n, t);
                uint size = SizeofResource(h, res);
                if (size < OleMagic.Length) return true;
                byte[] head = new byte[OleMagic.Length];
                Marshal.Copy(LockResource(LoadResource(h, res)), head, 0, head.Length);
                for (int i = 0; i < head.Length; i++) if (head[i] != OleMagic[i]) return true;
                found = Marshal.PtrToStringUni(n);
                return false;
            }, IntPtr.Zero);
            if (found == null) return null;
            EnumResourceLanguagesW(mod, (IntPtr)RT_RCDATA, found, (h, t, n, l, p) => { lang = l; return false; }, IntPtr.Zero);
        } finally {
            FreeLibrary(mod);
        }
        return Tuple.Create(found, lang);
    }

    // Copies the MSI stored in [exe] to [msi]; returns the resource name.
    public static string Extract(string exe, string msi) {
        var target = FindMsi(exe);
        if (target == null) throw new IOException(exe + " holds no MSI resource");
        IntPtr mod = LoadLibraryExW(exe, IntPtr.Zero, 0x2);
        try {
            IntPtr res = FindResourceW(mod, Marshal.StringToHGlobalUni(target.Item1), (IntPtr)RT_RCDATA);
            byte[] data = new byte[SizeofResource(mod, res)];
            Marshal.Copy(LockResource(LoadResource(mod, res)), data, 0, data.Length);
            File.WriteAllBytes(msi, data);
        } finally {
            FreeLibrary(mod);
        }
        return target.Item1;
    }

    public static string Replace(string exe, string msi) {
        var target = FindMsi(exe);
        if (target == null) throw new IOException(exe + " holds no MSI resource");
        byte[] data = File.ReadAllBytes(msi);
        IntPtr upd = BeginUpdateResourceW(exe, false);
        if (upd == IntPtr.Zero) throw new IOException("BeginUpdateResource failed: " + Marshal.GetLastWin32Error());
        if (!UpdateResourceW(upd, (IntPtr)RT_RCDATA, target.Item1, target.Item2, data, (uint)data.Length)) {
            int err = Marshal.GetLastWin32Error();
            EndUpdateResourceW(upd, true);
            throw new IOException("UpdateResource failed: " + err);
        }
        if (!EndUpdateResourceW(upd, false)) throw new IOException("EndUpdateResource failed: " + Marshal.GetLastWin32Error());
        return target.Item1;
    }
}
"@

$msis = @(Get-ChildItem -Path $Dir -Recurse -Filter *.msi)
$exes = @(Get-ChildItem -Path $Dir -Recurse -Filter *.exe)
if ($msis.Count -ne 1) { throw "expected one .msi under $Dir, found $($msis.Count)" }
# jpackage leaves its installers read-only (BeginUpdateResource then fails with ERROR_WRITE_PROTECT, 19).
foreach ($file in $msis + $exes) { $file.IsReadOnly = $false }
Update-Msi $msis[0].FullName

foreach ($exe in $exes) {
    # The exe is a separate jpackage run with its own MSI (and its own ProductCode, stored next to it), so its embedded
    # MSI is taken out, patched the same way and put back, instead of being swapped for the standalone .msi.
    $tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("keep-pins-" + [guid]::NewGuid() + ".msi")
    try {
        [MsiInExe]::Extract($exe.FullName, $tmp) | Out-Null
        Update-Msi $tmp
        $name = [MsiInExe]::Replace($exe.FullName, $tmp)
        Write-Host "$($exe.FullName): embedded MSI '$name' patched"
    } finally {
        Remove-Item $tmp -ErrorAction SilentlyContinue
    }
}
