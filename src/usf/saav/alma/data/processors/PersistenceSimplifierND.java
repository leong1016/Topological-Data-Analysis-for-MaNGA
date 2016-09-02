/*
 *     ALMA TDA - Contour tree based simplification and visualization for ALMA 
 *     data cubes.
 *     Copyright (C) 2016 PAUL ROSEN
 *     
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *     
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *     
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *     
 *     You may contact the Paul Rosen at <prosen@usf.edu>. 
 */
package usf.saav.alma.data.processors;

import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import usf.saav.alma.algorithm.mesh.Mesh;
import usf.saav.alma.algorithm.mesh.Mesh.Vertex;
import usf.saav.alma.algorithm.topology.AugmentedJoinTreeNode;
import usf.saav.alma.algorithm.topology.AugmentedJoinTreeNode.NodeType;
import usf.saav.alma.algorithm.topology.PersistenceSet;
import usf.saav.alma.data.ScalarFieldND;
import usf.saav.common.Callback;
import usf.saav.common.range.IntRange1D;

public abstract class PersistenceSimplifierND extends ScalarFieldND.Default implements ScalarFieldND, Runnable {

	private ScalarFieldND sf;
	private PersistenceSet ct;
	private Mesh cl;
	protected IntRange1D z;
	
	private float [] img;
	
	private boolean hasRun = false;
	
	protected Callback cb = null;


	public PersistenceSimplifierND( ScalarFieldND sf, PersistenceSet ct, Mesh cl, IntRange1D z, boolean runImmediately ){
		this( sf, ct, cl, z, runImmediately, true );
	}

	public PersistenceSimplifierND( ScalarFieldND sf, PersistenceSet ct, Mesh cl, IntRange1D z, boolean runImmediately, boolean verbose ){
		super( verbose );
		this.sf = sf;
		this.ct = ct;
		this.cl = cl;
		this.z  = z;
		if( runImmediately ) run( );
	}
	

	public PersistenceSet			getTree( ){				return ct; }
	public Mesh			getComponentList(){ 	return cl; }
	public ScalarFieldND			getScalarField( ){  	return sf; }
	
	@Override public int getSize() {	return sf.getSize();	}
	@Override public float getValue(int idx) { 	return img[idx]; }
	@Override public int[] getNeighbors(int nodeID) { 	return sf.getNeighbors(nodeID); }

	

	public abstract void setCallback( Object obj, String func_name );
	

	@Override
	public void run() {
		if( hasRun ){
			return;
		}
		
		print_info_message("Building 2D Simplification for Slice " + z);

		// Copy the existing field
		img = new float[sf.getSize()];
		for(int i = 0; i < img.length; i++){
			img[i] = sf.getValue(i);
		}
		
		Vector<AugmentedJoinTreeNode> workList = new Vector<AugmentedJoinTreeNode>();

		// Simplify the field, component by component
		for(int i = 0; i < ct.size(); i++){
			if( !ct.isActive(i) ){
				AugmentedJoinTreeNode n = ct.getNode(i);
				switch( n.getType() ){
					case LEAF_MAX: workList.add(n); break;
					case LEAF_MIN: workList.add(n); break;
					case MERGE:    break;
					case SPLIT:    break;
					case UNKNOWN:  break;
				}
			}
		}
		
		workList.sort( new AugmentedJoinTreeNode.ComparePersistenceAscending() );
		
		for( int i = 0; i < workList.size(); i++ ){
			AugmentedJoinTreeNode n = workList.get(i);
			//System.out.println( (i+1) + " of " + workList.size() + " (" + n.getPersistence() + ")" );
			simplify( n );
		}
		
		print_info_message("Build Complete for Slice " + z);

		hasRun = true;
		if( cb != null ){
			cb.call( this );
		}
		
	}
	
	private void simplify(AugmentedJoinTreeNode n) {
		AugmentedJoinTreeNode p = n.getPartner();
		
		if( p.getLocation() == n.getLocation() ) return;
		
		// Skip MERGE / LEAF
		if( n.getType() == NodeType.MERGE ) return;
		
		// Skip SPLIT / LEAF
		if( n.getType() == NodeType.SPLIT ) return;
		
		// Skip LEAF_MAX / LEAF_MIN
		if( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.LEAF_MIN ) return;
		
		// LEAF / SPLIT & LEAF / MERGE
		/*
		Queue<Integer> workList = null;
		Set<Integer>   compUsed = new HashSet<Integer>();
		Set<Integer>   pModify  = new HashSet<Integer>();

		if( n.getType() == NodeType.LEAF_MIN && p.getType() == NodeType.MERGE ) workList = new PriorityQueue<Integer>( 11, new CurrentFieldValueAscending() );
		if( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.SPLIT ) workList = new PriorityQueue<Integer>( 11, new CurrentFieldValueDescending() );
		
		float inval  = n.getBirth();
		float outval = n.getDeath();
		
		Set<Integer> start = new HashSet<Integer>( );
		for(int i : cl.get( n.getLocation() ).positions() ) start.add(i);

		Set<Integer> end   = new HashSet<Integer>( );
		for(int i : cl.get( p.getLocation() ).positions() ) end.add(i);
		
		compUsed.addAll(start);
		workList.addAll(start);
		//pModify.addAll(start);
		//pModify.addAll(end);
		
		while( !workList.isEmpty() ){
			int cur = workList.poll();
			
			// Add to list of components modified 
			pModify.add(cur);

			// If the component is the partner, we're done
			if( end.contains( cur ) ) break;
			
			// Add neighbors who haven't already been processed to the process queue
			for( int neighbor : sf.getNeighbors(cur) ){
				if( !compUsed.contains( neighbor ) ){
					workList.add(neighbor);
					compUsed.add(neighbor);
				}				
			}
		}
		

		if( n.getType() == NodeType.LEAF_MIN && p.getType() == NodeType.MERGE ){
			for( int pos : pModify ){
				img[pos] = Math.max( img[pos], outval );
				//img[pos] = -10;
			}
		}

		if( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.SPLIT ){
			for( int pos : pModify ){
				img[pos] = Math.min( img[pos], inval );
				//img[pos] = +10;
			}
		}
		*/
		
		
		// LEAF / MERGE
		if( ( n.getType() == NodeType.LEAF_MIN && p.getType() == NodeType.MERGE ) || 
				( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.SPLIT ) ) {

			Set<Integer>   compUsed = new HashSet<Integer>();
			Queue<Integer> workList = null;
			Set<Integer>   pModify  = new HashSet<Integer>();
			
			if( n.getType() == NodeType.LEAF_MIN && p.getType() == NodeType.MERGE ) workList = new PriorityQueue<Integer>( 11, new ComponentComparatorAscending() );
			if( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.SPLIT ) workList = new PriorityQueue<Integer>( 11, new ComponentComparatorDescending() );
	
			
			
			float inval  = n.getBirth();
			float outval = n.getDeath();
			
			compUsed.add( n.getLocation() );
			workList.add( n.getLocation() );
	
	
			while( !workList.isEmpty() ){
				int cur = workList.poll();
				
				// Add to list of components modified 
				pModify.add(cur);
	
				// If the component is the partner, we're done
				if( cur == p.getLocation() ) break;
				
				// Add neighbors who haven't already been processed to the process queue
				for( int neighbor : cl.get(cur).neighbors() ){
					if( !compUsed.contains( neighbor ) ){
						workList.add(neighbor);
						compUsed.add(neighbor);
					}				
				}
			}
	
			if( n.getType() == NodeType.LEAF_MIN && p.getType() == NodeType.MERGE ){
				for( Integer c : pModify ){
					Vertex cur = cl.get(c);
					for( int pos : cur.positions() )
						img[pos] = Math.max( img[pos], outval );
				}
			}
	
			// LEAF / SPLIT
			if( n.getType() == NodeType.LEAF_MAX && p.getType() == NodeType.SPLIT ){
				for( Integer c : pModify ){
					Vertex cur = cl.get(c);
					for( int pos : cur.positions() )
						img[pos] = Math.min( img[pos], inval );
				}
			}
		}

		//System.out.println( n.getLocation() + " " + n.getType().name() + " | " + p.getLocation() + " " + p.getType().name() + " " + inval + " " + outval + " " + pModify.size() );

	}
	

	

	class ComponentComparatorAscending implements Comparator<Integer>{
		@Override public int compare(Integer o1, Integer o2) {
			if( cl.get(o1).value() < cl.get(o2).value() ) return -1;
			if( cl.get(o1).value() > cl.get(o2).value() ) return  1;
			return 0;
		}
	}
	
	class ComponentComparatorDescending implements Comparator<Integer>{
		@Override public int compare(Integer o1, Integer o2) {
			if( cl.get(o1).value() < cl.get(o2).value() ) return  1;
			if( cl.get(o1).value() > cl.get(o2).value() ) return -1;
			return 0;
		}
	}
	
	class CurrentFieldValueAscending implements Comparator<Integer>{
		@Override public int compare(Integer o1, Integer o2) {
			if( img[o1] < img[o2] ) return -1;
			if( img[o1] > img[o2] ) return  1;
			return 0;
		}
	}
	
	class CurrentFieldValueDescending implements Comparator<Integer>{
		@Override public int compare(Integer o1, Integer o2) {
			if( img[o1] < img[o2] ) return  1;
			if( img[o1] > img[o2] ) return -1;
			return 0;
		}
	}
	
	
	class OriginalFieldValueComparator implements Comparator<Integer>{
		int inv = 1;
		OriginalFieldValueComparator( boolean invert ){
			inv = invert ? -1 : 1;
		}

		@Override
		public int compare(Integer o1, Integer o2) {
			if( sf.getValue(o1) < sf.getValue(o2) ) return -1*inv;
			if( sf.getValue(o1) > sf.getValue(o2) ) return  1*inv;
			return 0;
		}
		
	}




}