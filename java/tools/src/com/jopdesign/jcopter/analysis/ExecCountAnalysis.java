/*
 * This file is part of JOP, the Java Optimized Processor
 *   see <http://www.jopdesign.com/>
 *
 * Copyright (C) 2011, Stefan Hepp (stefan@stefant.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jopdesign.jcopter.analysis;

import com.jopdesign.common.AppInfo;
import com.jopdesign.common.MethodInfo;
import com.jopdesign.common.code.CallGraph;
import com.jopdesign.common.code.CallGraph.ContextEdge;
import com.jopdesign.common.code.ControlFlowGraph;
import com.jopdesign.common.code.ControlFlowGraph.BasicBlockNode;
import com.jopdesign.common.code.ControlFlowGraph.CFGEdge;
import com.jopdesign.common.code.ControlFlowGraph.CFGNode;
import com.jopdesign.common.code.ExecutionContext;
import com.jopdesign.common.code.InvokeSite;
import com.jopdesign.common.code.LoopBound;
import com.jopdesign.common.graphutils.BackEdgeFinder;
import com.jopdesign.common.graphutils.EdgeProvider;
import com.jopdesign.common.graphutils.GraphUtils;
import com.jopdesign.common.graphutils.LoopColoring;
import com.jopdesign.common.misc.Ternary;
import org.apache.bcel.generic.InstructionHandle;
import org.jgrapht.DirectedGraph;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Stefan Hepp (stefan@stefant.org)
 */
public class ExecCountAnalysis {

    private class InlineEdgeProvider implements EdgeProvider<ExecutionContext,ContextEdge> {

        private final Set<InvokeSite> newInvokeSites;

        private InlineEdgeProvider(Set<InvokeSite> newInvokeSites) {
            this.newInvokeSites = newInvokeSites;
        }

        @Override
        public Collection<ContextEdge> outgoingEdgesOf(ExecutionContext node) {
            Collection<ContextEdge> edges = callGraph.getOutgoingEdges(node);
            List<ContextEdge> result = new ArrayList<ContextEdge>(edges.size());

            for (ContextEdge edge : edges) {
                for (InvokeSite is : edge.getTarget().getCallString()) {
                    if (newInvokeSites.contains(is)) {
                        result.add(edge);
                    }
                }
            }
            return result;
        }

        @Override
        public ExecutionContext getEdgeTarget(ContextEdge edge) {
            return edge.getTarget();
        }
    }

    private final Map<ExecutionContext, Long> roots;
    private final CallGraph callGraph;

    private final Map<ExecutionContext,Long> nodeCount;
    private final Set<MethodInfo> changeSet;

    ////////////////////////////////////////////////////////////////////////////////////
    // Construction, initialization
    ////////////////////////////////////////////////////////////////////////////////////

    public ExecCountAnalysis(Map<ExecutionContext, Long> roots) {
        this.roots = new HashMap<ExecutionContext, Long>(roots);
        callGraph = AppInfo.getSingleton().getCallGraph();
        nodeCount =  new HashMap<ExecutionContext, Long>();
        changeSet = new HashSet<MethodInfo>(1);
    }

    public ExecCountAnalysis(CallGraph callGraph) {
        this.callGraph = callGraph;
        nodeCount =  new HashMap<ExecutionContext, Long>();
        changeSet = new HashSet<MethodInfo>(1);
        roots = new HashMap<ExecutionContext, Long>(callGraph.getRootNodes().size());
        for (ExecutionContext node : callGraph.getRootNodes()) {
            roots.put(node, 1L);
        }
    }

    public void initialize() {
        nodeCount.clear();

        // initialize roots
        nodeCount.putAll(roots);

        BackEdgeFinder<ExecutionContext,ContextEdge> finder = callGraph.getBackEdgeFinder();
        DirectedGraph<ExecutionContext,ContextEdge> dag = finder.createDAG();

        updateExecCounts(dag);
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Query the analysis results
    ////////////////////////////////////////////////////////////////////////////////////

    public long getExecCount(MethodInfo methodInfo) {
        return getExecCount(new ExecutionContext(methodInfo));
    }

    public long getExecCount(ExecutionContext context) {
        long count = 0;

        for (ExecutionContext ec : callGraph.getNodes(context)) {
            Long c = nodeCount.get(ec);
            if (c != null) {
                count += c;
            }
        }

        return count;
    }

    public long getExecCount(InvokeSite invokeSite) {
        return getExecCount(invokeSite.getInvoker(), invokeSite.getInstructionHandle());
    }

    public long getExecCount(MethodInfo method, InstructionHandle ih) {
        return getExecCount(new ExecutionContext(method), ih);
    }

    public long getExecCount(ExecutionContext context, InstructionHandle ih) {
        return getExecCount(context) * getExecFrequency(context, ih);
    }

    public long getExecFrequency(ExecutionContext context, InstructionHandle ih) {
        MethodInfo method = context.getMethodInfo();

        // By loading the CFG, loopbounds are attached to the blocks if the WCA tool is loaded
        ControlFlowGraph cfg = method.getCode().getControlFlowGraph(false);

        LoopColoring<CFGNode,CFGEdge> lc = cfg.getLoopColoring();
        BasicBlockNode node = cfg.getHandleNode(ih, true);
        if (node == null) {
            // Since the CFG does not represent the complete code, there might be some instructions without block
            // (exception handlers, ..)
            // THIS IS UNSAFE! but what can you do ...
            return 1;
        }

        long ef = 1;

        for (CFGNode hol : lc.getLoopColor(node)) {

            LoopBound lb = hol.getLoopBound();
            if (lb != null) {
                // TODO we might want to choose between upper/lower bound or even use an average value
                ef *= lb.getUpperBound(context);
            } else {
                // TODO magic number..
                ef *= 10;
            }

        }

        return ef;
    }

    public long getExecFrequency(InvokeSite invokeSite) {
        return getExecFrequency(invokeSite.getInvoker(), invokeSite.getInstructionHandle());
    }

    public long getExecFrequency(MethodInfo method, InstructionHandle ih) {
        return getExecFrequency(new ExecutionContext(method), ih);
    }

    ////////////////////////////////////////////////////////////////////////////////////
    // Notify of updates of the underlying callgraph, recalculate
    ////////////////////////////////////////////////////////////////////////////////////

    public void clearChangeSet() {
        changeSet.clear();
    }

    public Set<MethodInfo> getChangeSet() {
        return changeSet;
    }

    /**
     * Update the execution counts after inlining.
     * This must be called after the underlying callgraph has been updated!
     *
     * @param invokeSite the inlined invokesite.
     * @param invokee the inlined method.
     * @param newInvokeSites the set of new invokesites in the invoker
     */
    public void inline(InvokeSite invokeSite, MethodInfo invokee, Set<InvokeSite> newInvokeSites) {

        List<ExecutionContext> queue = new ArrayList<ExecutionContext>();

        for (ExecutionContext context : callGraph.getNodes(invokeSite.getInvoker())) {
            for (ExecutionContext child : callGraph.getChildren(context)) {
                if (child.getCallString().isEmpty() && child.getMethodInfo().equals(invokee)) {
                    // there can be at most one such node in the graph.. remove the total exec count of the
                    // inlined invokesite
                    nodeCount.put(child, nodeCount.get(child) - getExecCount(invokeSite));
                }
                else if (!child.getCallString().isEmpty() && newInvokeSites.contains(child.getCallString().top())) {
                    // This is a new node, sum up the execution counts of all invokesite instances
                    addExecCount(child, getExecCount(context, child.getCallString().top().getInstructionHandle()));

                    queue.add(child);
                }
            }
        }

        // update exec counts for all new nodes. A node is new if it contains one of the new invoke sites
        // We do not need to remove exec counts, since all nodes containing the old invokesite are now no
        // longer in the callgraph. We could however remove those nodes from our data structures in a
        // separate step before the callgraph is updated.

        // To do this, we create a temporary subgraph containing all new nodes and no back edges, and then traverse
        // it in topological order

        // TODO if the callgraph is compressed, we need to look down up to callstringLength for new nodes!

        DirectedGraph<ExecutionContext,ContextEdge> dag =
                GraphUtils.copyGraph(new InlineEdgeProvider(newInvokeSites), callGraph.getEdgeFactory(), queue, false);
        updateExecCounts(dag);

        // Despite all that is going on, the only *method* for which something changes in total is the inlined invokee
        changeSet.add(invokee);
    }


    ////////////////////////////////////////////////////////////////////////////////////
    // Private stuff
    ////////////////////////////////////////////////////////////////////////////////////

    private void addExecCount(ExecutionContext node, long count) {
        Long c = nodeCount.get(node);
        long val = (c == null) ? 0 : c;
        val += count;
        nodeCount.put(node, val);
    }

    private void updateExecCounts(DirectedGraph<ExecutionContext,ContextEdge> dag) {

        // For now, we just require the graph to be a DAG.

        // TODO for all back-edges, we should find some max recursion count and update reachable nodes accordingly..
        // For now, we just assume we have no recursion (backedges are only due to insufficient callgraph thinning)
        // or simply ignore recursion (unsafe, of course..)

        // for the rest of the graph, we can now use a topological order
        TopologicalOrderIterator<ExecutionContext,ContextEdge> topOrder =
                new TopologicalOrderIterator<ExecutionContext, ContextEdge>(dag);
        while (topOrder.hasNext()) {
            ExecutionContext next = topOrder.next();

            updateChilds(next, callGraph.getChildren(next));
        }
    }

    private void updateChilds(ExecutionContext context, List<ExecutionContext> childs) {

        long ecCount = nodeCount.get(context);
        List<ExecutionContext> emptyCSNodes = new LinkedList<ExecutionContext>();

        for (ExecutionContext child : childs) {
            long count;

            if (!child.getCallString().isEmpty()) {
                // simple case: if we have a callstring, the top entry is the invokesite in the invoker

                //long count = getExecCount(context, child.getCallString().top().getInstructionHandle());
                // This is faster but needs to be changed if we make getExecCount more precise for instructions
                count = ecCount * getExecFrequency(context, child.getCallString().top().getInstructionHandle());

                addExecCount(child, count);
            } else {
                // tricky case: no callstring, we need to find all invokesites in the invoker and sum up all exec counts
                emptyCSNodes.add(child);
            }
        }

        if (emptyCSNodes.isEmpty()) return;

        for (InvokeSite invokeSite : context.getMethodInfo().getCode().getInvokeSites()) {
            long count = getExecCount(context, invokeSite.getInstructionHandle());

            // for all methods which could be invoked, add the exec count
            for (ExecutionContext child : emptyCSNodes) {
                if (invokeSite.canInvoke(child.getMethodInfo()) == Ternary.TRUE) {
                    addExecCount(child, count);
                }
            }

        }
    }

}
