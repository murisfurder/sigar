/*
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of SIGAR.
 * 
 * SIGAR is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.sigar.vmware;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.hyperic.sigar.SigarLoader;
import org.hyperic.sigar.win32.RegistryKey;
import org.hyperic.sigar.win32.Win32Exception;

public class VMControlLibrary {
    public static final String REGISTRY_ROOT =
        "SOFTWARE\\VMware, Inc.";

    public static final String PROP_VMCONTROL_SHLIB =
        "vmcontrol.shlib";

    private static final String VMWARE_LIB =
        getProperty("lib.vmware", getVMwareLib().getPath());

    private static final String VMCONTROL_TAR =
        getProperty("control.tar", VMWARE_LIB + "/perl/control.tar");

    private static final String VMCONTROL = "vmcontrol";

    private static final String VMCONTROL_DLL =
        VMCONTROL + "lib.dll";

    private static final String VMCONTROL_SO =
        VMCONTROL + ".so";

    private static final String VMCONTROL_OBJ =
        getProperty("vmcontrol.o", "control-only/" + VMCONTROL + ".o");

    private static final String GCC =
        getProperty("bin.gcc", "/usr/bin/gcc");

    private static final String TAR =
        getProperty("bin.tar", "/bin/tar");

    private static final String LIBSSL =
        getProperty("libssl", "libssl.so.0.9.7");

    private static final String LIBCRYPTO =
        getProperty("libcrypto", "libcrypto.so.0.9.7");

    private static boolean isDebug = false;

    private static String getProperty(String key, String defval) {
        return System.getProperty("vmcontrol." + key, defval);
    }

    private static File getVMwareLib() {
        String location = "/usr/lib/vmware";
        File lib = new File(location);
        if (lib.exists()) {
            //running on a VMware host
            return lib;
        }
        else {
            //remote w/ api installed
            return new File(location + "-api");
        }
    }

    private static File getLib(String name) {
        File lib = new File(VMWARE_LIB, "lib/" + name);
        if (lib.isDirectory()) {
            lib = new File(lib, name);
        }
        return lib;
    }

    private static File getLibSSL() {
        return getLib(LIBSSL);
    }

    private static File getLibCrypto() {
        return getLib(LIBCRYPTO);
    }

    private static void exec(String[] args)
        throws IOException {

        Process proc = Runtime.getRuntime().exec(args);
        try {
            int exitVal = proc.waitFor();
            if (exitVal != 0) {
                String msg =
                    "exec" + Arrays.asList(args) +
                    " failed: " + exitVal;
                throw new IOException(msg);
            }
        } catch (InterruptedException e) {
        }
        if (isDebug) {
            System.out.println("exec" + Arrays.asList(args) + " OK");
        }
    }

    public static String getSharedLibrary() {
        return System.getProperty(PROP_VMCONTROL_SHLIB);
    }

    public static void setSharedLibrary(String lib) {
        System.setProperty(PROP_VMCONTROL_SHLIB, lib);
    }

    public static void link()
        throws IOException {

        link(VMCONTROL_SO);
    }

    private static void linkWin32() {
        List dlls = new ArrayList();

        RegistryKey root = null;
        try {
            root =
                RegistryKey.LocalMachine.openSubKey(REGISTRY_ROOT);

            String[] keys = root.getSubKeyNames();
            for (int i=0; i<keys.length; i++) {
                String name = keys[i];
                if (!name.startsWith("VMware ")) {
                    continue;
                }

                RegistryKey subkey = null;
                try {
                    subkey = root.openSubKey(name);
                    String path = subkey.getStringValue("InstallPath");
                    if (path == null) {
                        continue;
                    }
                    path = path.trim();
                    if (path.length() == 0) {
                        continue;
                    }
                    File dll = new File(path + VMCONTROL_DLL);
                    if (dll.exists()) {
                        //prefer VMware Server or VMware GSX Server
                        if (name.endsWith(" Server")) {
                            dlls.add(0, dll.getPath());
                        }
                        //Scripting API will also work
                        else if (name.endsWith(" API")) {
                            dlls.add(dll.getPath());
                        }
                    }
                } catch (Win32Exception e) {

                } finally {
                    if (subkey != null) {
                        subkey.close();
                    }
                }
            }
        } catch (Win32Exception e) {
        } finally {
            if (root != null) {
                root.close();
            }
        }

        if (dlls.size() != 0) {
            setSharedLibrary((String)dlls.get(0));
        }
    }

    public static void link(String name)
        throws IOException {

        if (SigarLoader.IS_WIN32) {
            linkWin32();
            return;
        }

        File out = new File(name).getAbsoluteFile();
        if (out.isDirectory()) {
            out = new File(out, VMCONTROL_SO);
        }

        boolean exists = out.exists();

        if (exists) {
            setSharedLibrary(out.getPath());
            return; //already linked
        }

        if (!new File(VMCONTROL_TAR).exists()) {
            return; //VMware not installed
        }

        File dir = out.getParentFile();
        if (!(dir.isDirectory() && dir.canWrite())) {
            throw new IOException("Cannot write to: " + dir);
        }

        File libssl = getLibSSL();
        File libcrypto = getLibCrypto();

        if (!libssl.exists()) {
            throw new FileNotFoundException(libssl.toString());
        }

        File obj = new File(dir, VMCONTROL_OBJ);
        if (!obj.exists()) {
            //extract vmcontrol.o
            String[] extract_args = {
                TAR,
                "-xf",
                VMCONTROL_TAR,
                "-C", dir.toString(),
                VMCONTROL_OBJ
            };

            exec(extract_args);
        }

        //create vmcontrol.so from vmcontrol.o
        List link_args = new ArrayList();

        link_args.add(GCC);
        link_args.add("-shared");
        link_args.add("-o");
        link_args.add(out.getPath());
        link_args.add(obj.getPath());

        //Skip rpath for ESX 3.x
        if (!new File(libssl.getParent(), "libc.so.6").exists()) {
            final String rpath = "-Wl,-rpath";

            link_args.add(rpath);
            link_args.add(libssl.getParent());

            //check if libcrypto is in a different directory
            if (!libssl.getParent().equals(libcrypto.getParent())) {
                link_args.add(rpath);
                link_args.add(libcrypto.getParent());
            }
        }

        link_args.add(libssl.getPath());
        link_args.add(libcrypto.getPath());

        exec((String[])link_args.toArray(new String[0]));

        setSharedLibrary(out.getPath());
    }

    public static boolean isLoaded() {
        return VMwareObject.LOADED;
    }

    public static void main(String[] args) throws Exception {
        isDebug = true;
        if (args.length == 0) {
            link();
        }
        else {
            link(args[0]);
        }

        String shlib = getSharedLibrary();
        if (shlib == null) {
            System.out.println("No library found");
        }
        else {
            System.out.println(PROP_VMCONTROL_SHLIB + "=" + shlib +
                               " (loaded=" + isLoaded() + ")");
        }
    }
}