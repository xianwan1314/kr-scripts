package com.omarea.krscript.executor;

import android.content.Context;

import com.omarea.common.shared.FileWrite;

import java.util.HashMap;
/**
 * Created by Hello on 2018/04/03.
 */

public class ExtractAssets {
    // 用于记录已经提取过的资源，避免重复提取浪费性能
    private static final HashMap<String, String> extractHisotry = new HashMap<>();

    private final Context context;

    public ExtractAssets(Context context) {
        this.context = context;
    }
    private String extract(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        if (extractHisotry.containsKey(fileName)) {
            return extractHisotry.get(fileName);
        }
        if (fileName.startsWith("file:///android_asset/")) {
            fileName = fileName.substring("file:///android_asset/".length());
        }
        return fileName;
    }

    private String extractScript(String fileName) {
        fileName = extract(fileName);
        String filePath = FileWrite.INSTANCE.writePrivateShellFile(fileName, fileName, context);
        if (filePath != null) {
            extractHisotry.put(fileName, filePath);
        }
        return filePath;
    }
    public String extractResource(String fileName) {
        if (fileName.endsWith(".sh")) {
            return extractScript(fileName);
        }
        fileName = extract(fileName);

        String filePath = FileWrite.INSTANCE.writePrivateFile(context.getAssets(), fileName, fileName, context);

        if (filePath != null) {
            extractHisotry.put(fileName, filePath);
        }
        return filePath;
    }

    public String extractResources(String dir) {
        dir = extract(dir);

        if (dir.startsWith("file:///android_asset/")) {
            dir = dir.substring("file:///android_asset/".length());
        } else if (dir.endsWith("/")) {
            dir = dir.substring(0, dir.length() - 1);
        }

        try {
            String[] files = context.getAssets().list(dir);
            if (files != null && files.length > 0) {
                for (String file : files) {
                    extractResources(dir + "/" + file);
                }
                String outputDir = getExtractPath(dir);
                extractHisotry.put(dir, outputDir);
                return outputDir;
            } else {
                return extractResource(dir);
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    public String getExtractPath(String file) {
        return FileWrite.INSTANCE.getPrivateFilePath(
                context,
                (file.startsWith("file:///android_asset/") ? (file.substring("file:///android_asset/".length())) : file)
        );
    }
}
