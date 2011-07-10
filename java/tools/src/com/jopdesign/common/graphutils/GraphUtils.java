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

package com.jopdesign.common.graphutils;

import com.jopdesign.common.graphutils.DFSTraverser.DFSEdgeType;
import com.jopdesign.common.graphutils.DFSTraverser.DFSVisitor;
import com.jopdesign.common.graphutils.DFSTraverser.EmptyDFSVisitor;
import org.jgrapht.DirectedGraph;
import org.jgrapht.EdgeFactory;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.Collection;

/**
 * @author Stefan Hepp (stefan@stefant.org)
 */
public class GraphUtils {

    public static <V,E> SimpleDirectedGraph<V,E> copyAcyclicGraph(DirectedGraph<V,E> graph) {
        BackEdgeFinder<V,E> finder = new BackEdgeFinder<V, E>(graph);
        return finder.createDAG();
    }

    public static <V,E> DirectedGraph<V,E> copyGraph(DirectedGraph<V,E> graph,
                                                     Collection<V> roots,
                                                     final boolean includeBackedges)
    {
        return copyGraph(new DefaultEdgeProvider<V, E>(graph), graph.getEdgeFactory(), roots, includeBackedges);
    }

    public static <V,E> DirectedGraph<V,E> copyGraph(EdgeProvider<V,E> provider, EdgeFactory<V,E> factory,
                                                     Collection<V> roots,
                                                     final boolean includeBackedges)
    {
        final DirectedGraph<V,E> newGraph = new DefaultDirectedGraph<V, E>(factory);

        DFSVisitor<V,E> visitor = new EmptyDFSVisitor<V, E>() {
            @Override
            public boolean visitNode(V parent, E edge, V node, DFSEdgeType type, Collection<E> outEdges, int depth) {
                if (type.isFirstVisit()) {
                    newGraph.addVertex(node);
                }
                if (type != DFSEdgeType.ROOT && (includeBackedges || type != DFSEdgeType.BACK_EDGE)) {
                    newGraph.addEdge(parent, node);
                }
                return true;
            }
        };

        DFSTraverser<V,E> traverser = new DFSTraverser<V, E>(visitor);
        traverser.traverse(provider, roots);

        return newGraph;
    }
}
