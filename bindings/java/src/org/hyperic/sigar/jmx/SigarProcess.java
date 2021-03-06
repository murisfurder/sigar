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

package org.hyperic.sigar.jmx;

import org.hyperic.sigar.ProcCpu;
import org.hyperic.sigar.ProcFd;
import org.hyperic.sigar.ProcMem;
import org.hyperic.sigar.ProcUtil;
import org.hyperic.sigar.Sigar;
import org.hyperic.sigar.SigarException;
import org.hyperic.sigar.SigarProxy;
import org.hyperic.sigar.SigarProxyCache;

/**
 * Implement the SigarProcessMBean to provide current process info
 * via JMX.
 */

public class SigarProcess implements SigarProcessMBean {

    private static final Long NOTIMPL = new Long(Sigar.FIELD_NOTIMPL);
    private Sigar sigarImpl;
    private SigarProxy sigar;
    private long pid = -1;

    public SigarProcess() {
        this.sigarImpl = new Sigar();
        this.sigar = SigarProxyCache.newInstance(sigarImpl);
    }

    public SigarProcess(SigarProxy sigar) {
        this.sigar = sigar;
    }

    public void close() {
        if (this.sigarImpl != null) {
            this.sigarImpl.close();
        }
    }

    private RuntimeException unexpectedError(String type,
                                             SigarException e) {
        String msg =
            "Unexected error in Sigar.get" + type +
            ": " + e.getMessage();
        return new IllegalArgumentException(msg);
    }
                                   
    private synchronized ProcMem getMem() {
        try {
            return this.sigar.getProcMem(getPid());
        } catch (SigarException e) {
            throw unexpectedError("Mem", e);
        }
    }

    private synchronized ProcCpu getCpu() {
        try {
            return this.sigar.getProcCpu(getPid());
        } catch (SigarException e) {
            throw unexpectedError("Cpu", e);
        }   
    }

    private synchronized ProcFd getFd() throws SigarException {
        return this.sigar.getProcFd(getPid());
    }

    public String getObjectName() throws SigarException {
        long pid = getPid();
        String name = this.sigar.getProcState(pid).getName();
        String cls = "unknown";
        if (name.startsWith("java")) {
            try {
                cls = ProcUtil.getJavaMainClass(this.sigar, pid);
            } catch (SigarException e) {}
        } //else XXX
        return
            AbstractMBean.MBEAN_DOMAIN + ":" +
            AbstractMBean.MBEAN_ATTR_TYPE + "=" + "Process" + "," +
            "Name" + "=" + name + "," +
            "Class" + "=" + cls + "," +
            "Pid" + "=" + pid;
    }

    public long getPid() {
        if (this.pid < 0) {
            return this.sigar.getPid();
        }
        else {
            return this.pid;
        }
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public Long getMemSize() {
        return new Long(getMem().getSize());
    }

    /**
     * @deprecated
     * @see getMemSize
     */
    public Long getMemVsize() {
        return getMemSize();
    }

    public Long getMemResident() {
        return new Long(getMem().getResident());
    }

    public Long getMemShare() {
        return new Long(getMem().getShare());
    }

    public Long getMemPageFaults() {
        return new Long(getMem().getPageFaults());
    }

    public Long getTimeUser() {
        return new Long(getCpu().getUser());
    }

    public Long getTimeSys() {
        return new Long(getCpu().getSys());
    }

    public Double getCpuUsage() {
        return new Double(getCpu().getPercent());
    }

    public Long getOpenFd() {
        try {
            return new Long(getFd().getTotal());
        } catch (SigarException e) {
            return NOTIMPL;
        }
    }
}
