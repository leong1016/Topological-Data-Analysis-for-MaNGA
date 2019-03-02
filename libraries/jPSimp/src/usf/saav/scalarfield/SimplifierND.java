/*
 *     jPSimp - Persistence calculation and simplification of scalar fields.
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
package usf.saav.scalarfield;
 
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;

import usf.saav.common.monitoredvariables.Callback;
import usf.saav.mesh.Mesh;
import usf.saav.mesh.Mesh.Vertex;
import usf.saav.topology.JoinTreeNode;
import usf.saav.topology.TopoTree;
import usf.saav.topology.TopoTreeNode;
import usf.saav.topology.TopoTreeNode.NodeType;

public abstract class SimplifierND extends ScalarFieldND.Default implements ScalarFieldND, Runnable {

	private ScalarFieldND sf;
	private TopoTree ct;
	private Mesh cl;

	private float [] img;

	private boolean hasRun = false;

	protected Callback cb = null;


	public SimplifierND( ScalarFieldND sf, TopoTree ct, Mesh cl, boolean runImmediately ){
		this( sf, ct, cl, runImmediately, true );
	}

	public SimplifierND( ScalarFieldND sf, TopoTree ct, Mesh cl, boolean runImmediately, boolean verbose ){
		super( verbose );
		this.sf = sf;
		this.ct = ct;
		this.cl = cl;
		if( runImmediately ) run( );
	}


	public TopoTree			getTree( ){				return ct; }
	public Mesh			getComponentList(){ 	return cl; }
	public ScalarFieldND			getScalarField( ){  	return sf; }

	@Override public int getSize() {	return sf.getSize();	}
	@Override public float getValue(int idx) { 	return img[idx]; }

	public abstract void setCallback( Object obj, String func_name );

	
	
	@Override
	public void run() {
		if( hasRun ){
			return;
		}

		print_info_message("Building Simplification");

		// Copy the existing field
		img = new float[sf.getSize()];
		for(int i = 0; i < img.length; i++){
			img[i] = sf.getValue(i);
		}

//		Vector<TopoTreeNode> workList = new Vector<TopoTreeNode>();
	    Queue<TopoTreeNode> workList = new PriorityQueue<TopoTreeNode>( 
	            sf.getSize(), 
	            new TopoTreeNode.CompareSimplePersistenceDescending()
	            );

		// Simplify the field, component by component
		for(int i = 0; i < ct.size(); i++){
		    if (ct.isPruning(i)) {
		        TopoTreeNode n = ct.getNode(i);
		        switch( n.getType() ){
        				case LEAF_MAX:
        			    case LEAF_MIN:
        				    workList.add(n);
        		            break;
        			    default:
        			        break;
        			}
		    }
		}
		
		while (!workList.isEmpty()) {
		    TopoTreeNode n = workList.poll();
		    TopoTreeNode p = n.getParent();
		    pruneLeaf(n, p);
	        modifyScalarField(n, p);
	        if (p.hasParent() && p.getChildCount() == 1) {
	            TopoTreeNode newVertex = reduceVertex(n, p);
	            if (workList.remove(newVertex)) {
	                workList.add(newVertex);
	            }
	        }
		}

		print_info_message("Build Complete");

		hasRun = true;
		if( cb != null ){
			cb.call( this );
		}

	}
	
	private void pruneLeaf(TopoTreeNode n, TopoTreeNode p) {
	    n.setParent(null);
	    p.removeChild((JoinTreeNode) n);
	}
	
	private TopoTreeNode reduceVertex(TopoTreeNode n, TopoTreeNode p) {
	    JoinTreeNode sbl = p.getChild(0);
        JoinTreeNode np = (JoinTreeNode) p.getParent();
        sbl.setParent(np);
        p.setParent(null);
        p.removeChild(sbl);
        np.removeChild((JoinTreeNode) p);
        np.addChild((JoinTreeNode) n);
        return sbl;
	}
	
	private void modifyScalarField(TopoTreeNode n, TopoTreeNode p) {

        Set<Integer>   compUsed = new HashSet<Integer>();
        Queue<Integer> workList = null;
        Set<Integer>   pModify  = new HashSet<Integer>();
        
        if( n.getType() == NodeType.LEAF_MIN ) 
            workList = new PriorityQueue<Integer>( 11, new ComponentComparatorAscending() );
        if( n.getType() == NodeType.LEAF_MAX ) 
            workList = new PriorityQueue<Integer>( 11, new ComponentComparatorDescending() );

        compUsed.add( n.getPosition() );
        workList.add( n.getPosition() );

        while( !workList.isEmpty() ){

            int cur = workList.poll();

            // Add to list of components modified
            pModify.add(cur);

            // If the component is the parent, we're done
            if( cur == p.getPosition() ) break;

            // Add neighbors who haven't already been processed to the process queue
            for( int neighbor : cl.get(cur).neighbors() ){
                if( !compUsed.contains( neighbor ) ){
                    workList.add(neighbor);
                    compUsed.add(neighbor);
                }
            }
        }
        
        // modify values
        for( Integer c : pModify ){
            Vertex cur = cl.get(c);
            for( int pos : cur.positions() ) {
                if( n.getType() == NodeType.LEAF_MIN ) 
                    img[pos] = Math.max( img[pos], p.getValue() );
                if( n.getType() == NodeType.LEAF_MAX ) 
                    img[pos] = Math.min( img[pos], p.getValue() );
            }
        }
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
