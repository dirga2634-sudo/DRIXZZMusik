// AIDL buat komunikasi ke ShellUserService yang jalan di proses terpisah (shell/ADB, via
// Shizuku UserService API -- bukan Shizuku#newProcess yang sudah di-deprecated).
package com.webtools.optimizer;

interface IShellService {
    String exec(in String command) = 1;
    void destroy() = 16777114;
}
