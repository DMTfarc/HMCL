/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsIndex;
import org.jackhuang.hellominecraft.launcher.utils.assets.AssetsObject;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftVersion;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersions;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.IOUtils;
import org.jackhuang.hellominecraft.utils.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.OS;

/**
 *
 * @author huang
 */
public final class MCUtils {

    public static File getAssetObject(Gson gson, String dir, String assetVersion, String name) throws IOException {
        File assetsDir = new File(dir, "assets");
        File indexDir = new File(assetsDir, "indexes");
        File objectsDir = new File(assetsDir, "objects");
        File indexFile = new File(indexDir, assetVersion + ".json");
        try {
            AssetsIndex index = (AssetsIndex) gson.fromJson(FileUtils.readFileToString(indexFile, "UTF-8"), AssetsIndex.class);

            String hash = ((AssetsObject) index.getFileMap().get(name)).getHash();
            return new File(objectsDir, hash.substring(0, 2) + "/" + hash);
        } catch(JsonSyntaxException e) {
            throw new IOException("Assets file format malformed.", e);
        }
    }

    private static int lessThan32(byte[] b, int x) {
        for (; x < b.length; x++) {
            if (b[x] < 32) {
                return x;
            }
        }
        return -1;
    }

    private static MinecraftVersionRequest getVersionOfOldMinecraft(ZipFile paramZipFile, ZipEntry paramZipEntry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(paramZipFile.getInputStream(paramZipEntry));

        byte[] arrayOfByte = "Minecraft Minecraft ".getBytes("ASCII");
        int j;
        if ((j = ArrayUtils.matchArray(tmp, arrayOfByte)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + arrayOfByte.length;

        if ((j = lessThan32(tmp, i)) < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        String ver = new String(tmp, i, j - i, "ASCII");
        r.version = ver;

        if (paramZipFile.getEntry("META-INF/MANIFEST.MF") == null) {
            r.type = MinecraftVersionRequest.Modified;
        } else {
            r.type = MinecraftVersionRequest.OK;
        }
        return r;
    }

    private static MinecraftVersionRequest getVersionOfNewMinecraft(ZipFile file, ZipEntry entry) throws IOException {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        byte[] tmp = NetUtils.getBytesFromStream(file.getInputStream(entry));

        byte[] str = "-server.txt".getBytes("ASCII");
        int j = ArrayUtils.matchArray(tmp, str);
        if (j < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        int i = j + str.length;
        i += 11;
        j = lessThan32(tmp, i);
        if (j < 0) {
            r.type = MinecraftVersionRequest.Unkown;
            return r;
        }
        r.version = new String(tmp, i, j - i, "ASCII");
        
        char ch = r.version.charAt(0);
        // 1.8.1+
        if(ch < '0' || ch > '9') {
            str = "Can't keep up! Did the system time change, or is the server overloaded?".getBytes("ASCII");
            j = ArrayUtils.matchArray(tmp, str);
            if (j < 0) {
                r.type = MinecraftVersionRequest.Unkown;
                return r;
            }
            i = -1;
            while (j > 0) {
                if(tmp[j] >= 48 && tmp[j] <= 57) {
                    i = j;
                    break;
                }
                j--;
            }
            if (i == -1) {
                r.type = MinecraftVersionRequest.Unkown;
                return r;
            }
            int k = i;
            while (tmp[k] >= 48 && tmp[k] <= 57 || tmp[k] == 46) k--;
            k++;
            r.version = new String(tmp, k, i - k + 1);
        }

        if (file.getEntry("META-INF/MANIFEST.MF") == null) {
            r.type = MinecraftVersionRequest.Modified;
        } else {
            r.type = MinecraftVersionRequest.OK;
        }
        return r;
    }

    public static MinecraftVersionRequest minecraftVersion(File file) {
        MinecraftVersionRequest r = new MinecraftVersionRequest();
        if (!file.exists()) {
            r.type = MinecraftVersionRequest.NotFound;
            return r;
        }
        if (!file.isFile()) {
            r.type = MinecraftVersionRequest.NotAFile;
            return r;
        }
        if (!file.canRead()) {
            r.type = MinecraftVersionRequest.NotReadable;
            return r;
        }
        ZipFile localZipFile = null;
        try {
            localZipFile = new ZipFile(file);
            ZipEntry minecraft = localZipFile
                    .getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null) {
                return getVersionOfOldMinecraft(localZipFile, minecraft);
            }
            ZipEntry main = localZipFile.getEntry("net/minecraft/client/main/Main.class");
            ZipEntry minecraftserver = localZipFile.getEntry("net/minecraft/server/MinecraftServer.class");
            if ((main != null) && (minecraftserver != null)) {
                return getVersionOfNewMinecraft(localZipFile, minecraftserver);
            }
            r.type = MinecraftVersionRequest.Invaild;
            return r;
        } catch (IOException localException) {
            HMCLog.warn("Zip file is invalid", localException);
            r.type = MinecraftVersionRequest.InvaildJar;
            return r;
        } finally {
            if (localZipFile != null) {
                try {
                    localZipFile.close();
                } catch (IOException ex) {
                    HMCLog.warn("Failed to close zip file", ex);
                }
            }
        }
    }

    public static File getLocation() {
        String localObject = "minecraft";
        String str1 = System.getProperty("user.home", ".");
        File file;
        OS os = OS.os();
        if (os == OS.LINUX) {
            file = new File(str1, '.' + (String) localObject + '/');
        } else if (os == OS.WINDOWS) {
            String str2;
            if ((str2 = System.getenv("APPDATA")) != null) {
                file = new File(str2, "." + (String) localObject + '/');
            } else {
                file = new File(str1, '.' + (String) localObject + '/');
            }
        } else if (os == OS.OSX) {
            file = new File(str1, "Library/Application Support/" + localObject);
        } else {
            file = new File(str1, localObject + '/');
        }
        return file;
    }

    public static boolean is16Folder(String path) {
        path = IOUtils.addSeparator(path);
        return new File(path, "versions").exists();
    }

    public static String minecraft() {
        String os = System.getProperty("os.name").trim().toLowerCase();
        if (os.contains("mac")) {
            return "minecraft";
        }
        return ".minecraft";
    }

    public static File getInitGameDir() {
        File gameDir = IOUtils.currentDir();
        if (gameDir.exists()) {
            gameDir = new File(gameDir, MCUtils.minecraft());
            if (!gameDir.exists()) {
                File newFile = MCUtils.getLocation();
                if(newFile.exists()) gameDir = newFile;
            }
        }
        return gameDir;
    }

    public static MinecraftVersion downloadMinecraft(File gameDir, String id, DownloadType sourceType) {
        String vurl = sourceType.getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(gameDir, "versions/" + id);
        File mvt = new File(vpath, id + ".json");
        File mvj = new File(vpath, id + ".jar");
        vpath.mkdirs();
        mvt.delete();
        mvj.delete();

        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
                .addTask(new FileDownloadTask(vurl + id + ".jar", IOUtils.tryGetCanonicalFile(mvj)).setTag(id + ".jar"))
                .start()) {
            MinecraftVersion mv = new Gson().fromJson(FileUtils.readFileToStringQuietly(mvt), MinecraftVersion.class);
            //File apath = new File(gameDir, "assets/indexes");
            //downloadMinecraftAssetsIndex(apath, mv.assets, sourceType);
            return mv;
        }
        return null;
    }

    public static boolean downloadMinecraftVersionJson(File gameDir, String id, DownloadType sourceType) {
        String vurl = sourceType.getProvider().getVersionsDownloadURL() + id + "/";
        File vpath = new File(gameDir, "versions/" + id);
        File mvv = new File(vpath, id + ".json"), moved = null;
        if (mvv.exists()) {
            moved = new File(vpath, id + "-renamed.json");
            mvv.renameTo(moved);
        }
        File mvt = new File(vpath, id + ".json");
        vpath.mkdirs();
        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(vurl + id + ".json", IOUtils.tryGetCanonicalFile(mvt)).setTag(id + ".json"))
                .start()) {
            if (moved != null) {
                moved.delete();
            }
            return true;
        } else {
            mvt.delete();
            if (moved != null) {
                moved.renameTo(mvt);
            }
            return false;
        }
    }

    public static boolean downloadMinecraftAssetsIndex(File assetsLocation, String assetsId, DownloadType sourceType) {
        String aurl = sourceType.getProvider().getIndexesDownloadURL();

        assetsLocation.mkdirs();
        File assetsIndex = new File(assetsLocation, "indexes/" + assetsId + ".json");
        File renamed = null;
        if (assetsIndex.exists()) {
            renamed = new File(assetsLocation, "indexes/" + assetsId + "-renamed.json");
            assetsIndex.renameTo(renamed);
        }
        if (TaskWindow.getInstance()
                .addTask(new FileDownloadTask(aurl + assetsId + ".json", IOUtils.tryGetCanonicalFile(assetsIndex)).setTag(assetsId + ".json"))
                .start()) {
            if (renamed != null) {
                renamed.delete();
            }
            return true;
        }
        if (renamed != null) {
            renamed.renameTo(assetsIndex);
        }
        return false;
    }

    public static MinecraftRemoteVersions getRemoteMinecraftVersions(DownloadType sourceType) throws IOException {
        String result = NetUtils.doGet(sourceType.getProvider().getVersionsListDownloadURL());
        return MinecraftRemoteVersions.fromJson(result);
    }
}
