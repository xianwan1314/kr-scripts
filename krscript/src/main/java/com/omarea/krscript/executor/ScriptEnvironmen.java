package com.omarea.krscript.executor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Environment;
import android.util.Log;

import com.omarea.common.shared.FileWrite;
import com.omarea.common.shell.KeepShell;
import com.omarea.common.shell.KeepShellPublic;
import com.omarea.common.shell.ShellTranslation;
import com.omarea.krscript.FileOwner;
import com.omarea.krscript.model.NodeInfoBase;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class ScriptEnvironmen {
    private static final String ASSETS_FILE = "file:///android_asset/";
    private static boolean inited = false;
    private static String environmentPath = "";
    // 此目录将添加到PATH尾部，作为应用程序提供的拓展程序库目录，如有需要则需要在初始化executor.sh之前为该变量赋值
    private static String TOOKIT_DIR = "";
    private static boolean rooted = false;
    private static KeepShell privateShell;
    @SuppressLint("StaticFieldLeak")
    private static ShellTranslation shellTranslation;

    public static boolean isInited() {
        return inited;
    }

    private static void init(Context context) {
        SharedPreferences configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE);

        init(context, configSpf.getString("executor", "kr-script/executor.sh"), configSpf.getString("toolkitDir", "kr-script/toolkit"));
    }
    /**
     * 获取框架的环境变量
     */
    private static HashMap<String, String> getEnvironment(Context context) {
        HashMap<String, String> params = new HashMap<>();
        params.put("TOOLKIT", TOOKIT_DIR);
        params.put("START_DIR", getStartPath(context));
        params.put("TEMP_DIR", context.getCacheDir().getAbsolutePath());
        FileOwner fileOwner = new FileOwner(context);
        int androidUid = fileOwner.getUserId();
        params.put("ANDROID_UID", String.valueOf(androidUid));
        try {
            params.put("APP_USER_ID", fileOwner.getFileOwner());
        } catch (Exception ignored) {
        }
        params.put("ANDROID_SDK", String.valueOf(VERSION.SDK_INT));
        params.put("ROOT_PERMISSION", rooted ? "true" : "false");
        params.put("SDCARD_PATH", Environment.getExternalStorageDirectory().getAbsolutePath());
        params.put("BUSYBOX", (new File(FileWrite.INSTANCE.getPrivateFilePath(context, "busybox")).exists()) ? FileWrite.INSTANCE.getPrivateFilePath(context, "busybox"):"busybox");
        try {
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            params.put("PACKAGE_NAME", context.getPackageName());
            params.put("PACKAGE_VERSION_NAME", packageInfo.versionName);
            params.put("PACKAGE_VERSION_CODE", String.valueOf((VERSION.SDK_INT >= Build.VERSION_CODES.P) ? packageInfo.getLongVersionCode(): packageInfo.versionCode));
        } catch (Exception ignored) {
        }
        return params;
    }

    /**
     * 初始化执行器
     *
     * @param context  Context
     * @param executor 执行器在Assets中的位置
     * @return 是否初始化成功
     */
    public static boolean init(Context context, String executor, String toolkitDir) {
        if (inited) {
            return true;
        }

        shellTranslation = new ShellTranslation(context.getApplicationContext());
        rooted = KeepShellPublic.INSTANCE.checkRoot();

        try {
            if (toolkitDir != null && !toolkitDir.isEmpty()) {
                TOOKIT_DIR = new ExtractAssets(context).extractResources(toolkitDir);
            }

            String fileName = executor;
            if (fileName.startsWith(ASSETS_FILE)) {
                fileName = fileName.substring(ASSETS_FILE.length());
            }

            InputStream inputStream = context.getAssets().open(fileName);
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes, 0, bytes.length);
            String envShell = new String(bytes, Charset.defaultCharset()).replaceAll("\r", "");

            HashMap<String, String> environment = getEnvironment(context);
            for (String key : environment.keySet()) {
                String value = environment.get(key);
                if (value == null) {
                    value = "";
                }
                envShell = envShell.replace("$({" + key + "})", value);
            }
            String outputPathAbs = FileWrite.INSTANCE.getPrivateFilePath(context, fileName);
            envShell = envShell.replace("$({EXECUTOR_PATH})", outputPathAbs);


            inited = FileWrite.INSTANCE.writePrivateFile(envShell.getBytes(Charset.defaultCharset()), fileName, context);
            if (inited) {
                environmentPath = outputPathAbs;
            }
            SharedPreferences.Editor configSpf = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit();
            configSpf.putString("executor", executor);
            configSpf.putString("toolkitDir", toolkitDir);
            configSpf.apply();
            privateShell = rooted ? KeepShellPublic.INSTANCE.getDefaultInstance() : new KeepShell(rooted);

            return inited;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String md5(String string) {
        if (string.isEmpty()) {
            return "";
        }

        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e("md5", e.toString());
        }

        return "";
    }

    /**
     * 写入缓存（脚本代码存入脚本文件）
     */
    private static String createShellCache(Context context, String script) {
        String outputPath = "kr-script/cache/" + md5(script) + ".sh";
        if (new File(outputPath).exists()) {
            return outputPath;
        }

        if (FileWrite.INSTANCE.writePrivateFile(((script.startsWith("#!/") ? "":"#!/system/bin/sh\n\n") + script)
                .replaceAll("\r\n", "\n")
                .replaceAll("\r\t", "\t")
                .replaceAll("\r", "\n")
                .getBytes(), outputPath, context)) {
            return FileWrite.INSTANCE.getPrivateFilePath(context, outputPath);
        }
        return "";
    }

    /**
     * 执行脚本
     */
    private static String extractScript(Context context, String fileName) {
        if (fileName.startsWith(ASSETS_FILE)) {
            fileName = fileName.substring(ASSETS_FILE.length());
        }
        return FileWrite.INSTANCE.writePrivateShellFile(fileName, fileName, context);
    }

    public static String executeResultRoot(Context context, String script, NodeInfoBase nodeInfoBase) {
        if (!inited) {
            init(context);
        }

        if (script == null || script.isEmpty()) {
            return "";
        }

        String script2 = script.trim();
        String path = script2.startsWith(ASSETS_FILE) ? extractScript(context, script2) : createShellCache(context, script);

        if (!inited) {
            init(context);
        }

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("\n");
        if (nodeInfoBase != null && !nodeInfoBase.getCurrentPageConfigPath().isEmpty()) {
            String parentPageConfigDir = nodeInfoBase.getPageConfigDir();
            String currentPageConfigPath = nodeInfoBase.getCurrentPageConfigPath();
            stringBuilder.append("export PAGE_CONFIG_DIR='").append(parentPageConfigDir).append("'\n");
            stringBuilder.append("export PAGE_CONFIG_FILE='").append(currentPageConfigPath).append("'\n");

            if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                stringBuilder.append("export PAGE_WORK_DIR='").append(new ExtractAssets(context).getExtractPath(parentPageConfigDir)).append("'\n");
                stringBuilder.append("export PAGE_WORK_FILE='").append(new ExtractAssets(context).getExtractPath(currentPageConfigPath)).append("'\n");
            } else {
                stringBuilder.append("export PAGE_WORK_DIR='").append(parentPageConfigDir).append("'\n");
                stringBuilder.append("export PAGE_WORK_FILE='").append(currentPageConfigPath).append("'\n");
            }
        }
        stringBuilder.append("\n");
        stringBuilder.append(environmentPath).append(" \"").append(path).append("\"");
        return (shellTranslation != null) ? shellTranslation.resolveRow(privateShell.doCmdSync(stringBuilder.toString())) : privateShell.doCmdSync(stringBuilder.toString());
    }

    private static String getStartPath(Context context) {
        String dir = FileWrite.INSTANCE.getPrivateFileDir(context);
        if (dir.endsWith("/")) {
            return dir.substring(0, dir.length() - 1);
        }
        return dir;
    }

    /*
    public static int getUserId() {
        int value = 0;
        try {
            Class<?> c = Class.forName("android.os.UserHandle");
            Method get = c.getMethod("getUserId", int.class);
            value = (int)(get.invoke(c, android.os.Process.myUid()));
        } catch (Exception ignored) {
        }
        return value;
    }*/




    /**
     *
     */


    private static String getExecuteScript(Context context, String script, String tag) {
        if (!inited) {
            init(context);
        }

        if (script == null || script.isEmpty()) {
            return "";
        }

        String script2 = script.trim();
        String cachePath;
        if (script2.startsWith(ASSETS_FILE)) {
            cachePath = extractScript(context, script2);
            if (cachePath == null) {
                cachePath = script;
                // String error = context.getString(R.string.script_losted) + setState;
                // Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            }
        } else {
            cachePath = createShellCache(context, script);
        }


        return environmentPath + " \"" + cachePath + "\" \"" + tag + "\"";
    }

    static Process getRuntime() {
        try {
            return Runtime.getRuntime().exec(rooted ? "su" : "sh");
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 使用执行器运行脚本
     *
     * @param context          Context
     * @param dataOutputStream Runtime进程的输出流
     * @param cmds             要执行的脚本
     * @param params           参数类别
     */
    public static void executeShell(
            Context context,
            DataOutputStream dataOutputStream,
            String cmds,
            HashMap<String, String> params,
            NodeInfoBase nodeInfo,
            String tag) {

        if (params == null) {
            params = new HashMap<>();
        }

        // 页面配置文件路径
        if (nodeInfo != null) {
            String parentPageConfigDir = nodeInfo.getPageConfigDir();
            String currentPageConfigPath = nodeInfo.getCurrentPageConfigPath();
            params.put("PAGE_CONFIG_DIR", parentPageConfigDir);
            params.put("PAGE_CONFIG_FILE", currentPageConfigPath);
            if (currentPageConfigPath.startsWith("file:///android_asset/")) {
                params.put("PAGE_WORK_DIR", new ExtractAssets(context).getExtractPath(parentPageConfigDir));
                params.put("PAGE_WORK_FILE", new ExtractAssets(context).getExtractPath(currentPageConfigPath));
            } else {
                params.put("PAGE_WORK_DIR", parentPageConfigDir);
                params.put("PAGE_WORK_FILE", currentPageConfigPath);
            }
        }
        StringBuilder envpCmds = new StringBuilder();
        if (!params.isEmpty()) {
            for (String param : params.keySet()) {
                String value = params.get(param);
                if (value == null) {
                    value = "";
                }
                envpCmds.append("export ").append(param).append("='").append(value.replaceAll("'", "'\\\\''")).append("'\n");
            }
        }
        try {
            String cache = getExecuteScript(context, cmds, tag);
            dataOutputStream.write(envpCmds.toString().getBytes(StandardCharsets.UTF_8));
            dataOutputStream.write(cache.getBytes(StandardCharsets.UTF_8));
            dataOutputStream.writeBytes("\nexit\nexit");
            dataOutputStream.flush();
            File file = new File(cache);
            if (file.exists() && file.isFile() && cache.endsWith(".sh")) {
                file.delete();
            }
        } catch (Exception ignored) {
        }
    }
}
