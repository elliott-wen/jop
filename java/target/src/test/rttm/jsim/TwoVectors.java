/*
  This file is part of JOP, the Java Optimized Processor
    see <http://www.jopdesign.com/>

  Copyright (C) 2008, Martin Schoeberl (martin@jopdesign.com)

  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/


/**
 * 
 */
package rttm.jsim;

import java.util.Vector;

import rtlib.SRSWQueue;

import com.jopdesign.io.IOFactory;
import com.jopdesign.io.SysDevice;
import com.jopdesign.sys.Native;
import com.jopdesign.sys.Startup;

/**
 * Test program for RTTM with two bound Queues.
 * 
 * @author Martin Schoeberl
 *
 */
public class TwoVectors {
	
	static SysDevice sys = IOFactory.getFactory().getSysDevice();
	
	private static Vector vAB = new Vector(10);
	private static Vector vBC = new Vector(10);

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (sys.nrCpu<3) {
			System.out.println("Not enogh CPUs for this example");
			System.exit(-1);
		}
		VecInserter<Object> ins = new VecInserter<Object>(vAB);
		VecMover<Object> mov = new VecMover<Object>(vAB, vBC);
		VecRemover<Object> rem = new VecRemover<Object>(vBC);

		Startup.setRunnable(mov, 0);
		Startup.setRunnable(rem, 1);

		// start the other CPUs
		sys.signal = 1;
		// run one Runnable on this CPU
		ins.run();

		System.out.println("wait");
		// wait for other CPUs to finish
		while (!(mov.finished && rem.finished)) {
			;
		}
	}
}
