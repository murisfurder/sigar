package net.hyperic.sigar.test;

import net.hyperic.sigar.Sigar;
import net.hyperic.sigar.SigarException;
import net.hyperic.sigar.SigarNotImplementedException;

public class TestProcArgs extends SigarTestCase {

    public TestProcArgs(String name) {
        super(name);
    }

    private boolean findArg(String[] args, String what) {
        boolean found = false;

        traceln("find=" + what);

        for (int i=0; i<args.length; i++) {
            traceln("   " + i + "=" + args[i]);

            if (args[i].equals(what)) {
                found = true;
            }
        }

        return found;
    }

    public void testCreate() throws Exception {
        Sigar sigar = getSigar();

        try {
            sigar.getProcArgs(getInvalidPid());
        } catch (SigarException e) {
        }

        try {
            String[] args = sigar.getProcArgs(sigar.getPid());

            if (getVerbose()) {
                findArg(args, TestProcArgs.class.getName());
            }

            assertTrue(args[0].indexOf("java") != -1);

            //hpux has a limit less than what these args will be
            if (!System.getProperty("os.name").equals("HP-UX")) {
                //and this test only works when run under ant
                //assertTrue(findArg(args, TestProcArgs.class.getName()));
            }
        } catch (SigarNotImplementedException e) {
            //ok; might not happen on win32
        }

	long[] pids = sigar.getProcList();

	for (int i=0; i<pids.length; i++) {
            try {
                String[] args = sigar.getProcArgs(pids[i]);
                traceln("pid=" + pids[i]);
                for (int j=0; j<args.length; j++) {
                    traceln("   " + j + "=>" + args[j] + "<==");
                }
            } catch (SigarException e) {
            }
	}
    }
}
