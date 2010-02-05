package scd_micro;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * Collects collisions in lists and then returns a list of collisions where
 * no two are equal.
 * @author Filip Pizlo
 */
class CollisionCollector {
	/** A hash set of collisions.  */
	private HashSet<Collision> collisions = new HashSet<Collision>();

	/** Add some collisions.  */
	public void addCollisions(List<Collision> collisions) {
		// Modified for JOP
		for(Collision c : collisions) {
			this.collisions.add(c);
		}
	}

	/** Get the list of collisions.   */
	public ArrayList<Collision> getCollisions() { 
		// Modified for JOP
		ArrayList<Collision> copy = new ArrayList<Collision>(collisions.size());
		for(Collision c : collisions) {
			copy.add(c);
		}
		return copy;
	}
}
