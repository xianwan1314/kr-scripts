package com.omarea.common.shell;

import java.io.IOException;
import java.io.OutputStream;
public class ShellExecutor {
    private static final String extraEnvPath = "";
    private static String defaultEnvPath = ""; // /sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin

    private static String getEnvPath() {
        // FIXME:非root模式下，默认的 TMPDIR=/data/local/tmp 变量可能会导致某些需要写缓存的场景（例如使用source指令）脚本执行失败！
        if (extraEnvPath != null && !extraEnvPath.isEmpty()) {
            if (defaultEnvPath.isEmpty()) {
                try {
                    String path = System.getProperty("PATH");
                    if (!path.isEmpty()) {
                        defaultEnvPath = path;
                    } else {
                        throw new RuntimeException("未能获取到$PATH参数");
                    }
                } catch (Exception ex) {
                    defaultEnvPath = "/bin:/sbin:/system/sbin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin";
                }
            }

            return ( "PATH=" + defaultEnvPath + ":" + extraEnvPath);
        }

        return null;
    }

    private static Process getProcess(String run) throws IOException {
        String env = getEnvPath();
        /*
        // 部分机型会有Aborted错误
        if (env != null) {
            return runtime.exec(run, new String[]{
                env
            });
        }
        */
        Process process = Runtime.getRuntime().exec(run);
        if (env != null) {
            OutputStream outputStream = process.getOutputStream();
            outputStream.write("export ".getBytes());
            outputStream.write(env.getBytes());
            outputStream.write("\n".getBytes());
            outputStream.flush();
        }
        return process;
    }

    public static Process getSuperUserRuntime() throws IOException {
        return getProcess("su");
    }

    public static Process getRuntime() throws IOException {
        return getProcess("sh");
    }
}
