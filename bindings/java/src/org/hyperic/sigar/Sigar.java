package org.hyperic.sigar;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.List;
import java.util.Map;

import org.hyperic.jni.ArchLoaderException;
import org.hyperic.jni.ArchNotSupportedException;

/**
 * The Sigar class provides access to the sigar objects containing
 * system information.  The Sigar object itself maintains internal
 * state specific to each platform.  It also implements the SigarProxy
 * interface which provides caching at the Java level.
 */
public class Sigar implements SigarProxy {

    private static String loadError = null;

    public static final long FIELD_NOTIMPL = -1;

    /**
     * The Sigar java version.
     */
    public static final String VERSION_STRING =
        SigarVersion.VERSION_STRING;

    /**
     * The Sigar native version.
     */
    public static final String NATIVE_VERSION_STRING;
    
    /**
     * The date on which sigar.jar was built.
     */
    public static final String BUILD_DATE =
        SigarVersion.BUILD_DATE;

    /**
     * The date on which the sigar native binary was built.
     */
    public static final String NATIVE_BUILD_DATE;
        
    private static boolean enableLogging =
        "true".equals(System.getProperty("sigar.nativeLogging"));

    private static SigarLoader loader = new SigarLoader(Sigar.class);
    private FileSystemMap mounts = null;

    private boolean open = false;
    int sigarWrapper = 0; //holds the sigar_t *
    long longSigarWrapper = 0; //same, but where sizeof(void*) > sizeof(int)

    // lastCpu is used to calculate the cpuPerc;
    private Cpu lastCpu;
    private Cpu[] lastCpuList;

    static {
        boolean loaded = false;
        try {
            loadLibrary();
            loaded = true;
        } catch (SigarException e) {
            loadError = e.getMessage();
            try {
                SigarLog.debug(loadError, e);
            } catch (NoClassDefFoundError ne) {
                //no log4j.jar
                System.err.println(loadError);
                e.printStackTrace();
            }
        }
        if (loaded) {
            NATIVE_VERSION_STRING = getNativeVersion();
            NATIVE_BUILD_DATE = getNativeBuildDate();
        }
        else {
            NATIVE_VERSION_STRING = NATIVE_BUILD_DATE = "N/A";
        }
    }

    public static void load() throws SigarException {
        if (loadError != null) {
            throw new SigarException(loadError);
        }
    }

    private static void loadLibrary() throws SigarException {
        try {
            if (SigarLoader.IS_WIN32 &&
                System.getProperty("os.version").equals("4.0"))
            {
                String lib =
                    loader.findJarPath("pdh.dll") +
                    File.separator + "pdh.dll";
                loader.systemLoad(lib);
            }
            loader.load();
        } catch (ArchNotSupportedException e) {
            throw new SigarException(e.getMessage());
        } catch (ArchLoaderException e) {
            throw new SigarException(e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            throw new SigarException(e.getMessage());
        }
    }

    /**
     * Format size in bytes to a human readable string.
     *
     * @param size The size to format.
     * @return The formatted string.
     */
    public static native String formatSize(long size);

    private static native String getNativeVersion();
    private static native String getNativeBuildDate();

    /**
     * Allocate and initialize the native Sigar object.
     */
    public Sigar() {
        try {
            open();
            this.open = true;
        } catch (SigarException e) {
            if (enableLogging) {
                e.printStackTrace();
            }
        } catch (UnsatisfiedLinkError e) {
            if (enableLogging) {
                e.printStackTrace();
            }
        }
        if (enableLogging) {
            enableLogging(true);
        }
    }

    protected void finalize() {
        close();
    }
    
    private native void open() throws SigarException;

    /**
     * Release any native resources associated with this sigar instance.
     * The sigar object is no longer usable after it has been closed.
     * If the close method is not called directly, the finalize method will
     * call it if the Sigar object is garbage collected.
     */
    public synchronized void close() {
        if (this.open) {
            nativeClose();
            this.open = false;
        }
    }

    private native int nativeClose();

    /**
     * Get pid of the current process.
     * @exception SigarException on failure.
     */
    public native long getPid();


    /**
     * Get pid for the Windows service with the given name.
     * This method is implemented on Windows only as a helper
     * for PTQL.
     */
    public native long getServicePid(String name) throws SigarException;

    /**
     * Send a signal to a process.
     *
     * @param pid The process id.
     * @param signum The signal number.
     * @exception SigarException on failure.
     */
    public native void kill(long pid, int signum) throws SigarException;

    /**
     * Get system memory info.
     * @exception SigarException on failure.
     */
    public Mem getMem() throws SigarException {
        return Mem.fetch(this);
    }

    /**
     * Get system swap info.
     * @exception SigarException on failure.
     */
    public Swap getSwap() throws SigarException {
        return Swap.fetch(this);
    }

    /**
     * Get system cpu info.
     * @exception SigarException on failure.
     */
    public Cpu getCpu() throws SigarException {
        return Cpu.fetch(this);
    }

    static void pause(int millis) {
        try {
            Thread.sleep(millis);
        } catch(InterruptedException e) { }
    }

    static void pause() {
        pause(500);
    }

    /**
     * Get system CPU info in percentage format. (i.e. fraction of 1)
     * @exception SigarException on failure.
     */
    public CpuPerc getCpuPerc() throws SigarException {
        Cpu oldCpu;

        if (this.lastCpu == null){
            oldCpu = getCpu();
            pause();
        }
        else {
            oldCpu = this.lastCpu;
        }

        this.lastCpu = getCpu();

        return CpuPerc.calculate(oldCpu, this.lastCpu);
    }

    /**
     * Get system per-CPU info in percentage format. (i.e. fraction of 1)
     * @exception SigarException on failure.
     */
    public CpuPerc[] getCpuPercList() throws SigarException {
        Cpu[] oldCpuList;

        if (this.lastCpuList == null){
            oldCpuList = getCpuList();
            pause();
        }
        else {
            oldCpuList = this.lastCpuList;
        }

        this.lastCpuList = getCpuList();

        int curLen = this.lastCpuList.length;
        int oldLen = oldCpuList.length;

        CpuPerc[] perc =
            new CpuPerc[curLen < oldLen ? curLen : oldLen];
        
        for (int i=0; i<curLen; i++) {
            perc[i] =
                CpuPerc.calculate(oldCpuList[i],
                                  this.lastCpuList[i]);
        }

        return perc;
    }

    /**
     * Get system resource limits.
     * @exception SigarException on failure.
     */
    public ResourceLimit getResourceLimit() throws SigarException {
        return ResourceLimit.fetch(this);
    }

    /**
     * Get system uptime info.
     * @exception SigarException on failure.
     */
    public Uptime getUptime() throws SigarException {
        return Uptime.fetch(this);
    }

    /**
     * Get system load average.
     * @exception SigarException on failure.
     * @return The system load averages for the past 1, 5, and 15 minutes.
     */
    public native double[] getLoadAverage() throws SigarException;

    /**
     * Get system process list.
     * @exception SigarException on failure.
     * @return Array of process ids.
     */
    public native long[] getProcList() throws SigarException;

    /**
     * Get system process stats.
     * @exception SigarException on failure.
     */
    public ProcStat getProcStat() throws SigarException {
        return ProcStat.fetch(this);
    }

    private long convertPid(String pid) throws SigarException {
        if (pid.equals("$$")) {
            return getPid();
        }

        return Long.parseLong(pid);
    }

    /**
     * Get process memory info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcMem getProcMem(long pid) throws SigarException {
        return ProcMem.fetch(this, pid);
    }

    public ProcMem getProcMem(String pid) throws SigarException {
        return getProcMem(convertPid(pid));
    }

    public ProcMem getMultiProcMem(String query) throws SigarException {
        return MultiProcMem.get(this, query);
    }

    /**
     * Get process state info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcState getProcState(long pid) throws SigarException {
        return ProcState.fetch(this, pid);
    }

    public ProcState getProcState(String pid) throws SigarException {
        return getProcState(convertPid(pid));
    }

    /**
     * Get process time info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcTime getProcTime(long pid) throws SigarException {
        return ProcTime.fetch(this, pid);
    }

    public ProcTime getProcTime(String pid) throws SigarException {
        return getProcTime(convertPid(pid));
    }

    /**
     * Get process cpu info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcCpu getProcCpu(long pid) throws SigarException {
        return ProcCpu.get(this, pid);
    }

    public ProcCpu getProcCpu(String pid) throws SigarException {
        return getProcCpu(convertPid(pid));
    }

    public MultiProcCpu getMultiProcCpu(String query) throws SigarException {
        return MultiProcCpu.get(this, query);
    }

    /**
     * Get process credential info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcCred getProcCred(long pid) throws SigarException {
        return ProcCred.fetch(this, pid);
    }

    public ProcCred getProcCred(String pid) throws SigarException {
        return getProcCred(convertPid(pid));
    }

    /**
     * Get process credential names.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcCredName getProcCredName(long pid) throws SigarException {
        return ProcCredName.fetch(this, pid);
    }

    public ProcCredName getProcCredName(String pid) throws SigarException {
        return getProcCredName(convertPid(pid));
    }

    /**
     * Get process file descriptor info.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcFd getProcFd(long pid) throws SigarException {
        return ProcFd.fetch(this, pid);
    }

    public ProcFd getProcFd(String pid) throws SigarException {
        return getProcFd(convertPid(pid));
    }

    /**
     * Get process current working directory.
     * @param pid The process id.
     * @exception SigarException on failure.
     */
    public ProcExe getProcExe(long pid) throws SigarException {
        return ProcExe.fetch(this, pid);
    }

    public ProcExe getProcExe(String pid) throws SigarException {
        return getProcExe(convertPid(pid));
    }

    /**
     * Get process arguments.
     * @param pid The process id.
     * @return Array of argument strings.
     * @exception SigarException on failure.
     */
    public native String[] getProcArgs(long pid) throws SigarException;

    public String[] getProcArgs(String pid) throws SigarException {
        return getProcArgs(convertPid(pid));
    }

    /**
     * Get process environment.
     * @param pid The process id.
     * @return Map of environment strings.
     * @exception SigarException on failure.
     */
    public Map getProcEnv(long pid) throws SigarException {
        return ProcEnv.getAll(this, pid);
    }

    public Map getProcEnv(String pid) throws SigarException {
        return getProcEnv(convertPid(pid));
    }

    /**
     * Get process environment variable value.
     * This method is intended to avoid the overhead
     * of creating a Map with all variables if only
     * a single variable is needed.
     * @param pid The process id.
     * @param key Environment variable name.
     * @return Environment variable value.
     * @exception SigarException on failure.
     */
    public String getProcEnv(long pid, String key) throws SigarException {
        return ProcEnv.getValue(this, pid, key);
    }

    public String getProcEnv(String pid, String key) throws SigarException {
        return getProcEnv(convertPid(pid), key);
    }

    /**
     * Get process loaded modules.<p>
     * Supported Platforms: Linux, Solaris and Windows.
     * @param pid The process id.
     * @return List of loaded modules.
     * @exception SigarException on failure.
     */
    private native List getProcModulesNative(long pid) throws SigarException;

    public List getProcModules(long pid) throws SigarException {
        return getProcModulesNative(pid);
    }

    public List getProcModules(String pid) throws SigarException {
        return getProcModules(convertPid(pid));
    }

    /**
     * Find the pid of the process which is listening on the given port.<p>
     * Supported Platforms: Linux, Windows 2003, Windows XP, AIX.
     * @param protocol NetFlags.CONN_TCP or NetFlags.CONN_UDP.
     * @param port The port number.
     * @return pid of the process.
     * @exception SigarException on failure.
     */
    public native long getProcPort(int protocol, long port)
        throws SigarException;

    /**
     * @param protocol "tcp" or "udp".
     * @param port The port number.
     * @return pid of the process.
     * @exception SigarException on failure.
     */
    public long getProcPort(String protocol, String port)
        throws SigarException {

        return getProcPort(NetFlags.getConnectionProtocol(protocol),
                           Integer.parseInt(port));
    }

    /**
     * Get the cumulative cpu time for the calling thread.
     */
    public ThreadCpu getThreadCpu() throws SigarException {
        return ThreadCpu.fetch(this, 0);
    }

    private native FileSystem[] getFileSystemListNative() throws SigarException;

    /**
     * Get list of file systems.
     * @exception SigarException on failure.
     */
    public FileSystem[] getFileSystemList() throws SigarException {
        FileSystem[] fslist = getFileSystemListNative();
        if (this.mounts != null) {
            this.mounts.init(fslist);
        }
        return fslist;
    }

    /**
     * Get file system usage.
     * @param name Name of the directory on which filesystem is mounted.
     * @exception SigarException on failure.
     */
    public FileSystemUsage getFileSystemUsage(String name)
        throws SigarException {
        if (name == null) {
            throw new SigarException("name cannot be null");
        }
        return FileSystemUsage.fetch(this, name);
    }

    /**
     * Get file system usage of a mounted directory.
     * This method checks that the given directory is mounted.
     * Unlike getFileSystemUsage() which only requires that the
     * directory exists within a mounted file system.
     * This method will also check that NFS servers are reachable via RPC
     * before attempting to get the file system stats to prevent application
     * hang when an NFS server is down.
     * @param name Name of the directory on which filesystem is mounted.
     * @exception SigarException If given directory is not mounted.
     * @exception NfsUnreachableException If NFS server is unreachable.
     * @see org.hyperic.sigar.Sigar#getFileSystemUsage
     */
    public FileSystemUsage getMountedFileSystemUsage(String name)
        throws SigarException, NfsUnreachableException {

        FileSystem fs = getFileSystemMap().getFileSystem(name);

        if (fs == null) {
            throw new SigarException(name + " is not a mounted filesystem");
        }

        if (fs instanceof NfsFileSystem) {
            NfsFileSystem nfs = (NfsFileSystem)fs;
            if (!nfs.ping()) {
                throw nfs.getUnreachableException();
            }
        }

        return FileSystemUsage.fetch(this, name);
    }

    public FileSystemMap getFileSystemMap()
        throws SigarException {

        if (this.mounts == null) {
            this.mounts = new FileSystemMap();
        }

        getFileSystemList(); //this.mounts.init()

        return this.mounts;
    }

    public FileInfo getFileInfo(String name)
        throws SigarException {
        return FileInfo.fetchFileInfo(this, name);
    }

    public FileInfo getLinkInfo(String name)
        throws SigarException {
        return FileInfo.fetchLinkInfo(this, name);
    }

    public DirStat getDirStat(String name)
        throws SigarException {
        return DirStat.fetch(this, name);
    }

    public DirUsage getDirUsage(String name)
        throws SigarException {
        return DirUsage.fetch(this, name);
    }

    /**
     * Get list of cpu infomation.
     * @exception SigarException on failure.
     */
    public native CpuInfo[] getCpuInfoList() throws SigarException;

    private native Cpu[] getCpuListNative() throws SigarException;

    /**
     * Get list of per-cpu metrics.
     * @exception SigarException on failure.
     */
    public Cpu[] getCpuList() throws SigarException {
        return getCpuListNative();
    }

    /**
     * Get list of network routes.
     * @exception SigarException on failure.
     */
    public native NetRoute[] getNetRouteList() throws SigarException;

    /**
     * Get list of network connections.
     * @exception SigarException on failure.
     */
    public native NetConnection[] getNetConnectionList(int flags)
        throws SigarException;

    /**
     * Get the TCP listen address for the given port.
     * If the port is not bound to a specific address,
     * the loopback address will be returned.
     * If there is not a listener on the given port, null will be returned.
     */    
    public String getNetListenAddress(long port)
        throws SigarException {

        int flags = NetFlags.CONN_SERVER | NetFlags.CONN_TCP;

        NetConnection[] connections =
            getNetConnectionList(flags);

        for (int i=0; i<connections.length; i++) {
            NetConnection conn = connections[i];

            if (conn.getState() != NetFlags.TCP_LISTEN) {
                continue;
            }

            if (conn.getLocalPort() == port) {
                String address =
                    conn.getLocalAddress();

                if (address.equals(NetFlags.ANY_ADDR)) {
                    return NetFlags.LOOPBACK_ADDRESS;
                }
                else if (address.equals(NetFlags.ANY_ADDR_V6)) {
                    return NetFlags.LOOPBACK_ADDRESS_V6;
                }
                else {
                    return address;
                }
            }
        }

        return null;
    }

    public String getNetListenAddress(String port)
        throws SigarException {

        return getNetListenAddress(Long.parseLong(port));
    }

    public NetStat getNetStat()
        throws SigarException {
        return new NetStat(this);
    }
    
    public native Who[] getWhoList()
        throws SigarException;

    /**
     * Get general network info.
     * @exception SigarException on failure.
     */
    public NetInfo getNetInfo()
        throws SigarException {
        return NetInfo.fetch(this);
    }

    /**
     * Get network interface configuration info.
     * @exception SigarException on failure.
     */
    public NetInterfaceConfig getNetInterfaceConfig(String name)
        throws SigarException {
        return NetInterfaceConfig.fetch(this, name);
    }

    /**
     * Get default network interface configuration info.
     * Iterates getNetInterfaceList(), returning the first
     * available ethernet interface.
     * @exception SigarException on failure.
     */
    public NetInterfaceConfig getNetInterfaceConfig()
        throws SigarException {

        String[] interfaces = getNetInterfaceList();

        for (int i=0; i<interfaces.length; i++) {
            String name = interfaces[i];
            NetInterfaceConfig ifconfig;

            try {
                ifconfig = getNetInterfaceConfig(name);
            } catch (SigarException e) {
                continue;
            }

            long flags = ifconfig.getFlags();
            if ((flags & NetFlags.IFF_UP) <= 0) {
                continue;
            }
            if ((flags & NetFlags.IFF_POINTOPOINT) > 0) {
                continue;
            }
            if ((flags & NetFlags.IFF_LOOPBACK) > 0) {
                continue;
            }

            return ifconfig;
        }

        String msg =
            "No ethernet interface available";
        throw new SigarException(msg);
    }

    /**
     * Get network interface stats.
     * @exception SigarException on failure.
     */
    public NetInterfaceStat getNetInterfaceStat(String name)
        throws SigarException {
        return NetInterfaceStat.fetch(this, name);
    }

    /**
     * Get the list of configured network interface names.
     * @exception SigarException on failure.
     */
    public native String[] getNetInterfaceList() throws SigarException;

    /**
     * Prompt for a password, disabling terminal echo
     * during user input.
     * @param prompt Text printed before disabling echo
     * @return Text entered by the user.
     * @throws IOException If input could not be read.
     * @throws SigarNotImplementedException If the native method
     * is not implemented on the current platform.
     */

    native static String getPasswordNative(String prompt)
        throws IOException, SigarNotImplementedException;

    /**
     * Prompt for a password, disabling terminal echo
     * during user input if possible.
     * @param prompt Text printed before disabling echo
     * @return Text entered by the user.
     * @throws IOException If input could not be read.
     */
    public static String getPassword(String prompt)
        throws IOException
    {
        try {
            return getPasswordNative(prompt);
        } catch (IOException e) {
            throw e;
        } catch (SigarNotImplementedException e) {
            //fallthrough
        }

        //fallback if native .so was not loaded or not supported
        System.out.print(prompt);

        return (new BufferedReader(new InputStreamReader(System.in))).
            readLine();
    }

    /**
     * Reliably retrieve the FQDN for a machine
     *
     * @return The fully qualified domain name of the machine.
     * @exception SigarException on failure.
     */
    public native String getFQDN() throws SigarException;

    /**
     * Enabling logging in the native Sigar code.
     * This method will hook log4j into the Sigar
     * native logging methods.  Note that the majority
     * of logging in the native code is only at the DEBUG
     * level.
     */
    public void enableLogging(boolean value) {
        if (value) {
            SigarLog.enable(this);
        }
        else {
            SigarLog.disable(this);
        }
    }
}