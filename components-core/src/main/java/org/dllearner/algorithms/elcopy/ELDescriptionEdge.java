/**
 * Copyright (C) 2007-2011, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dllearner.algorithms.elcopy;

import org.semanticweb.owlapi.model.OWLProperty;

/**
 * A (directed) edge in an EL OWLClassExpression tree. It consists of an edge
 * label, which is an object property, and the EL OWLClassExpression tree
 * the edge points to.
 * 
 * @author Jens Lehmann
 *
 */
public class ELDescriptionEdge {

	private OWLProperty label;
	
	private ELDescriptionNode node;

	/**
	 * Constructs and edge given a label and an EL OWLClassExpression tree.
	 * @param label The label of this edge.
	 * @param tree The tree the edge points to (edges are directed).
	 */
	public ELDescriptionEdge(OWLProperty label, ELDescriptionNode tree) {
		this.label = label;
		this.node = tree;
	}
	
	/**
	 * @param label the label to set
	 */
	public void setLabel(OWLProperty label) {
		this.label = label;
	}

	/**
	 * @return The label of this edge.
	 */
	public OWLProperty getLabel() {
		return label;
	}

	/**
	 * @return The EL OWLClassExpression tree 
	 */
	public ELDescriptionNode getNode() {
		return node;
	}
	
	public boolean isObjectProperty(){
		return label.isOWLObjectProperty();
	}
	
	@Override
	public String toString() {
		return "--" + label + "--> " + node.toDescriptionString(); 
	}
	
}
