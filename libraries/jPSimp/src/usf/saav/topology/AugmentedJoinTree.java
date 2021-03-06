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
package usf.saav.topology;

import java.util.Comparator;
import java.util.Stack;

import usf.saav.mesh.Mesh;
import usf.saav.topology.TopoTree;
import usf.saav.topology.TopoTreeNode;

public abstract class AugmentedJoinTree extends AugmentedJoinTreeBase implements TopoTree, Runnable {
	
	protected Mesh cl;
	
	private Comparator<? super JoinTreeNode> comparator;

	
	protected AugmentedJoinTree( Mesh cl, Comparator<? super JoinTreeNode> comparator ){
		this.cl = cl;
		this.comparator = comparator;
	}
	
	@Override
	public void run() {
		print_info_message( "Building tree..." );

		// Build a join tree.
		JoinTree jt = new JoinTree( cl, comparator );
		jt.run();

	    head = simpleProcessTree(jt.getRoot());
	     
	    calculateMaxPersistenceAndGlobalExtreme(head);
		calculateMaxVolumn(head);
		
//        for(int i = 0; i < size(); i++){
//            float per = getPersistence(i);
//            if( Float.isNaN(per) ) {
//                global_extreme = (AugmentedJoinTreeNode) getNode(i);
//            }
//        }
		
		print_info_message( "Building tree complete" );
	}
	
    // Paul's code, not in use any more.
    protected  AugmentedJoinTreeNode processTree(JoinTreeNode current) {
        int cumulatedVolumn = current.getVolumn();
        float cumulatedHyperVolumn = current.getAbsoluteHyperVolumn();
        while (current.childCount() == 1) {
            current = current.getChild(0);
            cumulatedVolumn += current.getVolumn();
            cumulatedHyperVolumn += current.getAbsoluteHyperVolumn();
        }
        if (current.childCount() == 0) {
            return createTreeNode(current.getPosition(), current.getValue(), cumulatedVolumn, cumulatedHyperVolumn);
        } else {
            AugmentedJoinTreeNode prev = processTree(current.getChild(0));
            int i = 1;
            while(i < current.childCount()) {
                AugmentedJoinTreeNode newChild = processTree(current.getChild(i));
                prev = createTreeNode(current.getPosition(), current.getValue(), 
                                      prev.getVolumn() + newChild.getVolumn(), 
                                      prev.getAbsoluteHyperVolumn() + newChild.getAbsoluteHyperVolumn(),
                                      prev, newChild);
                i++;
            }
            prev.addVolumn(cumulatedVolumn);
            prev.addHyperVolumn(cumulatedHyperVolumn);
            return prev;
        }
    }
	
    protected AugmentedJoinTreeNode simpleProcessTree(JoinTreeNode current) {
        int cumulatedVolumn = current.getVolumn();
        float cumulatedHyperVolumn = current.getAbsoluteHyperVolumn();
        while (current.childCount() == 1) {
            current = current.getChild(0);
            cumulatedVolumn += current.getVolumn();
            cumulatedHyperVolumn += current.getAbsoluteHyperVolumn();
        }
        if (current.childCount() == 0) {
            nodes.add(createTreeNode(current.getPosition(), current.getValue(), cumulatedVolumn, cumulatedHyperVolumn));
            return (AugmentedJoinTreeNode)nodes.lastElement();
        } else {
            AugmentedJoinTreeNode tmp = createTreeNode(current.getPosition(), current.getValue(), cumulatedVolumn, cumulatedHyperVolumn);
            for (int i = 0; i < current.childCount(); i++) {
                AugmentedJoinTreeNode newChild = simpleProcessTree(current.getChild(i));
                tmp.addChild(newChild);
                newChild.setParent(tmp);
                tmp.addVolumn(newChild.getVolumn());
                tmp.addHyperVolumn(newChild.getAbsoluteHyperVolumn());
            }
            nodes.add(tmp);
            return tmp;
        }
    }

    // Paul's code, not in use any more.
    protected void calculateMaxPersistence(JoinTreeNode root){
        print_info_message( "Finding Persistence");
        
        JoinTreeNode head = processTree(root);
        
        Stack<JoinTreeNode> pstack = new Stack<JoinTreeNode>( );
        pstack.push( head );
        
        while( !pstack.isEmpty() ){
            JoinTreeNode curr = pstack.pop();
            
            // leaf is only thing in the stack, done
            if( pstack.isEmpty() && curr.childCount() == 0 ) {
                global_extreme = (AugmentedJoinTreeNode) curr;
                global_extreme_value = curr.getValue();
                break;
            }
            
            // saddle point, push children onto stack
            if( curr.childCount() == 2 ){
                pstack.push(curr);
                pstack.push((JoinTreeNode)curr.getChild(0));
                pstack.push((JoinTreeNode)curr.getChild(1));
            }
            
            // leaf node, 2 options
            if( curr.childCount() == 0 ) {
                JoinTreeNode sibling = pstack.pop();
                JoinTreeNode parent  = pstack.pop();
                
                // sibling is a saddle, restack.
                if( sibling.childCount() == 2 ){
                    pstack.push( parent );
                    pstack.push( curr );
                    pstack.push( sibling );
                }
                
                // sibling is a leaf, we can match a partner.
                if( sibling.childCount() == 0 ){
                    // curr value is closer to parent than sibling
                    if( Math.abs(curr.getValue()-parent.getValue()) < Math.abs(sibling.getValue()-parent.getValue()) ){
                        curr.setPartner(parent);
                        parent.setPartner(curr);
                        pstack.push( sibling );
                    }
                    // sibling value is closer to parent than curr
                    else {
                        sibling.setPartner(parent);
                        parent.setPartner(sibling);
                        pstack.push( curr );
                    }
                    max_persistence = Math.max(max_persistence,parent.getPersistence());
                }
            }
        }
    }
    
    protected void calculateMaxPersistenceAndGlobalExtreme(JoinTreeNode head) {
        JoinTreeNode minNode = null;
        float minValue = Float.MAX_VALUE;
        JoinTreeNode maxNode = null;
        float maxValue = Float.MIN_VALUE;
        Stack<JoinTreeNode> pstack = new Stack<JoinTreeNode>( );
        pstack.push(head);
        while (!pstack.isEmpty()) {
            JoinTreeNode curr = pstack.pop();
            if (curr.getValue() < minValue) {
                minNode = curr;
                minValue = curr.getValue();
            }
            if (curr.getValue() > maxValue) {
                maxNode = curr;
                maxValue = curr.getValue();
            }
            for (JoinTreeNode child : curr.getChildren()) {
                pstack.push(child);
            }
        }
        this.max_persistence = Math.abs(maxValue - minValue);
        if (this.comparator.compare(minNode, maxNode) < 0) {
            this.global_extreme = (AugmentedJoinTreeNode) maxNode;
            this.global_extreme_value = maxValue;
        } else {
            this.global_extreme = (AugmentedJoinTreeNode) minNode;
            this.global_extreme_value = maxValue;
        }
    }
    
    protected void calculateMaxVolumn(JoinTreeNode head) {
        int volumn = Integer.MIN_VALUE;
        float hypervolumn = Float.MIN_VALUE;
        for (JoinTreeNode child : head.getChildren()) {
            int v = child.getVolumn();
            float h = Math.abs(child.getAbsoluteHyperVolumn() - head.getValue() * head.getVolumn());
            if (v > volumn)
                volumn = v;
            if (h > hypervolumn)
                hypervolumn = h;
        }
        max_volumn = volumn;
        max_hypervolumn = hypervolumn;
    }
    
    // Debugging only
    protected void printTree(JoinTreeNode head, int level) {
        String spaces = new String(new char[level]).replace("\0", "+");
        System.out.println(spaces+Math.round(head.getValue()*10000));
        for (JoinTreeNode child : head.children) {
            this.printTree(child, level+1);
        }
    }
    
    // Debugging only
    protected void printSaddle(JoinTreeNode head) {
        if (head.childCount() == 1) {
            this.printSaddle(head.getChild(0));
        } else if (head.childCount() > 1) {
            System.out.println("Root of the tree: "+Math.round(head.getValue()*10000));
        }
    }

    public AugmentedJoinTreeNode getGlobalExtreme(){ return global_extreme; }
    public Float getGlobalExtremeValue(){ return global_extreme_value; }
    
    public boolean checkTree() {
        return checkTreeBase(head);
    }
    
    protected boolean checkTreeBase(JoinTreeNode current) {
        if (current == null || current.getChildCount() == 0)
            return true;
        for (JoinTreeNode c : current.getChildren()) {
            if (!checkTreeBase(c) || !c.parent.equals(current))
                return false;
        }
        return true;
    }

	
	public abstract class AugmentedJoinTreeNode extends JoinTreeNode implements TopoTreeNode {
		
		AugmentedJoinTreeNode( int loc, float val, int volumn, float hypervolumn ){
			super(loc, val, volumn, hypervolumn);
		}
		
		protected AugmentedJoinTreeNode( int loc, float val, int volumn, float hypervolumn, AugmentedJoinTreeNode c0, AugmentedJoinTreeNode c1 ){
			super(loc, val, volumn, hypervolumn);
			this.addChild(c0);
			this.addChild(c1);
		}

	}
	
	protected abstract AugmentedJoinTreeNode createTreeNode( int loc, float val, int volumn, float hypervolumn );
	protected abstract AugmentedJoinTreeNode createTreeNode( int loc, float val, int volumn, float hypervolumn, AugmentedJoinTreeNode c0, AugmentedJoinTreeNode c1 );

	

}
