package com.bkw.hotfixdemo.helper;


import android.content.Context;
import android.util.Log;

import org.joor.Reflect;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;

/**
 * 插桩式-热修复
 */
public class HotFixHelper {
    private static final String TAG = "HotFixHelper";
    private static final String PATCH_DIR = "patch_dir";

    /** 加载dex文件
     * @param context
     * @param assetName
     * @return
     */
    public static boolean loadPatch(Context context, String assetName) {
        File patchDir = new File(context.getFilesDir(), PATCH_DIR);
        try {
            FileUtil.makeAndEnsureDirExisted(patchDir);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "create patch dir failed");
            return false;
        }
        File patchFile = new File(patchDir, assetName);
        return FileUtil.copyAsset2Dst(context, assetName, patchFile);
    }

    /** 获取dex文件集合
     * @param context
     * @param assetName
     */
    public static void tryInjectDex(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile != null && patchFile.exists()) {
            ArrayList<File> files = new ArrayList<>();
            files.add(patchFile);
            try {
                injectDex(context, context.getClassLoader(), files);
                Log.d(TAG, "inject dex success!");
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "inject dex failed:" + e.toString());
            }
        }
    }

    public static boolean deletePatchFile(Context context, String assetName) {
        File patchFile = new File(new File(context.getFilesDir(), PATCH_DIR), assetName);
        if (patchFile == null || !patchFile.exists()) {
            return false;
        }
        return patchFile.delete();
    }

    /** 解析dex文件
     * @param context
     * @param loader
     * @param extraDexFiles
     * @throws IOException
     */
    private static void injectDex(Context context, ClassLoader loader, ArrayList<File> extraDexFiles)
            throws IOException {
        // 获取系统ClassLoader的pathList对象
        Object pathList = Reflect.on(loader).field("pathList").get();

        // 调用makePathElements构造补丁的dexElements
        ArrayList<IOException> suppressedExceptions = new ArrayList<>();
        Object[] patchDexElements = makePathElements(pathList, extraDexFiles,
                FileUtil.getDexOptDir(context), suppressedExceptions);
        if (suppressedExceptions.size() > 0) {
            for (IOException e : suppressedExceptions) {
                Log.w(TAG, "Exception in makePathElement", e);
                throw e;
            }
        }

        // 将补丁Dex注入到系统ClassLoader的pathList对象的dexElements的最前面
        expandElementsArray(pathList, patchDexElements);
    }

    private static Object[] makePathElements(Object dexPathList, ArrayList<File> files, File optimizedDirectory,
                                             ArrayList<IOException> suppressedExceptions) {
        return Reflect.on(dexPathList).call("makePathElements", files,
                optimizedDirectory, suppressedExceptions).get();
    }

    private static void expandElementsArray(Object pathList, Object[] extraElements) {
        Object[] originalDexElements = Reflect.on(pathList).field("dexElements").get();
        Object[] combined = (Object[]) Array.newInstance(originalDexElements.getClass().getComponentType(),
                originalDexElements.length + extraElements.length);
        // 注意此处拷贝顺序，将补丁Dex放到了最前面
        System.arraycopy(extraElements, 0, combined, 0, extraElements.length);
        System.arraycopy(originalDexElements, 0, combined, extraElements.length, originalDexElements.length);
        Reflect.on(pathList).set("dexElements", combined);
    }
}

