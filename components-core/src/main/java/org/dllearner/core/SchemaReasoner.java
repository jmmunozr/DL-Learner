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

package org.dllearner.core;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dllearner.core.owl.DatatypePropertyHierarchy;
import org.dllearner.core.owl.Hierarchy;
import org.dllearner.core.owl.ObjectPropertyHierarchy;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLObjectProperty;

/**
 * Reasoning requests related to the schema of the knowledge base.
 * 
 * @author Jens Lehmann
 *
 */
public interface SchemaReasoner {
	
	/**
	 * Returns all named classes, which are not satisfiable, i.e. cannot 
	 * have instances.
	 * @return The set of inconsistent classes.
	 */
	public Set<OWLClass> getInconsistentClasses();	
	
	/**
	 * Returns the domain of this object property. (Theoretically, there could
	 * be more than one domain axiom. However, this can be considered a modelling
	 * error.)
	 * @param objectProperty An object property in the knowledge base.
	 * @return The rdfs:domain of <code>objectProperty</code>
	 */
	public OWLClassExpression getDomain(OWLObjectProperty objectProperty);
	
	/**
	 * Returns the domain of this data property.
	 * @param datatypeProperty An data property in the knowledge base.
	 * @return The rdfs:domain of <code>datatypeProperty</code>
	 */	
	public OWLClassExpression getDomain(OWLDataProperty datatypeProperty);
	
	/**
	 * Returns the range of this object property.
	 * @param objectProperty An object property in the knowledge base.
	 * @return The rdfs:range of <code>objectProperty</code>
	 */	
	public OWLClassExpression getRange(OWLObjectProperty objectProperty);
	
	/**
	 * Returns the range of this data property.
	 * @param datatypeProperty An data property in the knowledge base.
	 * @return The rdfs:range of <code>datatypeProperty</code>
	 */		
	public OWLDataRange getRange(OWLDataProperty datatypeProperty);
	
	/**
	 * Checks whether <code>superClass</code> is a super class of <code>subClass</code>.
	 * @param superClass The (supposed) super class.
	 * @param subClass The (supposed) sub class.
	 * @return Whether <code>superClass</code> is a super class of <code>subClass</code>.
	 */
	public boolean isSuperClassOf(OWLClassExpression superClass, OWLClassExpression subClass);	
	
	/**
	 * Checks whether <code>class1</code> is equivalent to <code>class2</code>.
	 * @param class1 The first class.
	 * @param class2 The second class2.
	 * @return Whether <code>class1</code> is equivalent to <code>class2</code>.
	 */
	public boolean isEquivalentClass(OWLClassExpression class1, OWLClassExpression class2);	
	
	/**
	 * Checks whether <code>class1</code> is disjoint with <code>class2</code>.
	 * @param class1 The first class.
	 * @param class2 The second class2.
	 * @return Whether <code>class1</code> is disjoint with <code>class2</code>.
	 */
	public boolean isDisjoint(OWLClass class1, OWLClass class2);
		
	/**
	 * Returns all asserted owl:equivalence class axioms for the given class.
	 * @param namedClass A named class in the background knowledge.
	 * @return A set of descriptions asserted to be equal to the named class.
	 */
	public Set<OWLClassExpression> getAssertedDefinitions(OWLClass namedClass);
	
	/**
	 * Checks which of <code>superClasses</code> are super classes of <code>subClass</code>
	 * @param superClasses A set of (supposed) super classes.
	 * @param subClasses The (supposed) sub class.
	 * @return The subset of <code>superClasses</code>, which satisfy the superclass-subclass relationship.
	 */
	public Set<OWLClassExpression> isSuperClassOf(Set<OWLClassExpression> superClasses, OWLClassExpression subClasses);	

	/**
	 * Computes and returns the class hierarchy of the knowledge base.
	 *
	 * @return The subsumption hierarchy of this knowledge base.
	 */
	public Hierarchy getClassHierarchy();	
	
	/**
	 * Returns direct super classes in the class hierarchy.
	 * 
	 * @param description
	 *            Atomic concept, top, or bottom.
	 * @return A set of more general concepts.
	 */
	public SortedSet<OWLClassExpression> getSuperClasses(OWLClassExpression description);

	/**
	 * Returns direct sub classes in the class hierarchy.
	 * 
	 * @param description
	 *            Atomic concept, top, or bottom.
	 * @return A set of more special concepts.
	 */
	public SortedSet<OWLClassExpression> getSubClasses(OWLClassExpression description);

	/**
	 * Computes and returns the object property hierarchy of the knowledge base.
	 * @return The object property hierarchy of the knowlege base.
	 */
	public ObjectPropertyHierarchy getObjectPropertyHierarchy();
	
	/**
	 * Returns more general concepts in the subsumption hierarchy.
	 * 
	 * @see OWLObjectPropertyHierarchy#getMoreGeneralRoles(OWLObjectProperty)
	 * @param objectProperty
	 *            Atomic concept, top, or bottom.
	 * @return A set of more general concepts.
	 */
	public SortedSet<OWLObjectProperty> getSuperProperties(OWLObjectProperty objectProperty);

	/**
	 * Returns more special concepts in the subsumption hierarchy.
	 * 
	 * @see OWLObjectPropertyHierarchy#getMoreSpecialRoles(OWLObjectProperty)
	 * @param objectProperty
	 *            Atomic concept, top, or bottom.
	 * @return A set of more special concepts.
	 */
	public SortedSet<OWLObjectProperty> getSubProperties(OWLObjectProperty objectProperty);

	/**
	 * TODO Outdated in OWL 2, because the universal role is the most general.
	 * @see OWLObjectPropertyHierarchy#getMostGeneralRoles()
	 * @return The most general roles.
	 */
	public TreeSet<OWLObjectProperty> getMostGeneralProperties();

	/**
	 * TODO Outdated in OWL, because the bottom role is the most specific.
	 * @see OWLObjectPropertyHierarchy#getMostSpecialRoles()
	 * @return The most special roles.
	 */
	public TreeSet<OWLObjectProperty> getMostSpecialProperties();

	/**
	 * Computes and returns the data property hierarchy of the knowledge base.
	 * @return The data property hierarchy of the knowlege base.
	 */	
	public DatatypePropertyHierarchy getDatatypePropertyHierarchy();
	
	/**
	 * Returns more general concepts in the subsumption hierarchy.
	 * 
	 * @see OWLObjectPropertyHierarchy#getMoreGeneralRoles(OWLObjectProperty)
	 * @param dataProperty
	 *            Atomic concept, top, or bottom.
	 * @return A set of more general concepts.
	 */
	public SortedSet<OWLDataProperty> getSuperProperties(OWLDataProperty dataProperty);

	/**
	 * Returns more special concepts in the subsumption hierarchy.
	 * 
	 * @see OWLObjectPropertyHierarchy#getMoreSpecialRoles(OWLObjectProperty)
	 * @param dataProperty
	 *            Atomic concept, top, or bottom.
	 * @return A set of more special concepts.
	 */
	public SortedSet<OWLDataProperty> getSubProperties(OWLDataProperty dataProperty);

	/**
	 * @see OWLObjectPropertyHierarchy#getMostGeneralRoles()
	 * @return The most general roles.
	 */
	public TreeSet<OWLDataProperty> getMostGeneralDatatypeProperties();

	/**
	 * @see OWLObjectPropertyHierarchy#getMostSpecialRoles()
	 * @return The most special roles.
	 */
	public TreeSet<OWLDataProperty> getMostSpecialDatatypeProperties();
	
}
