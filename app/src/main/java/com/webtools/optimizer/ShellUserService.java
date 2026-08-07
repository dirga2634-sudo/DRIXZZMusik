package com.webtools.optimizer;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Kode ini jalan di PROSES TERPISAH dengan identitas shell (ADB), disediakan Shizuku lewat
 * UserService API. Karena bukan proses app Android biasa, API yang butuh app Context (kayak
 * registerReceiver, getContentResolver) TIDAK akan jalan di sini -- cukup Runtime.exec() polos
 * buat jalanin command shell, itu cukup buat kebutuhan baca dumpsys/proc.
 */
public class ShellUserService extends IShellService.Stub {

    public ShellUserService() {
        // Shizuku UserService butuh constructor kosong.
    }

    @Override
    public String exec(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
