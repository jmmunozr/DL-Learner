package org.dllearner.algorithms.versionspace;

import org.dllearner.algorithms.versionspace.complexity.ClassExpressionDepthComplexityModel;
import org.dllearner.algorithms.versionspace.complexity.ClassExpressionLengthComplexityModel;
import org.dllearner.algorithms.versionspace.complexity.ComplexityModel;
import org.dllearner.algorithms.versionspace.complexity.HybridComplexityModel;
import org.dllearner.algorithms.versionspace.operator.ComplexityBoundedOperator;
import org.dllearner.algorithms.versionspace.operator.ComplexityBoundedOperatorALC;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.ToStringRenderer;
import org.semanticweb.owlapi.manchestersyntax.renderer.ManchesterOWLSyntaxOWLObjectRendererImpl;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.io.File;
import java.util.*;

/**
 * @author Lorenz Buehmann
 */
public class ComplexityBoundedVersionSpaceGenerator extends AbstractVersionSpaceGenerator {

	private ComplexityBoundedOperator operator;

	public ComplexityBoundedVersionSpaceGenerator(ComplexityBoundedOperator operator) {
		this.operator = operator;
	}

	@Override
	public VersionSpace generate() {
		// the root node is owl:Thing
		DefaultVersionSpaceNode rootNode = new DefaultVersionSpaceNode(topConcept);

		// create the version space
		VersionSpace versionSpace = new VersionSpace(rootNode);

		// keep track of already visited(refined) nodes
		Set<DefaultVersionSpaceNode> visited = new HashSet<>();

		// the list of nodes we have to process
		Queue<DefaultVersionSpaceNode> todo = new ArrayDeque<>();
		todo.add(rootNode);

		while(!todo.isEmpty()) {
			// pick next concept to process
			DefaultVersionSpaceNode parent = todo.poll();

			// compute all refinements
			Set<OWLClassExpression> refinements = operator.refine(parent.getHypothesis());

			// add child node and edge to parent for each refinement
			for (OWLClassExpression ref : refinements) {
				DefaultVersionSpaceNode child = new DefaultVersionSpaceNode(ref);

				if(!child.equals(parent)) {
					versionSpace.addVertex(child);
					versionSpace.addEdge(parent, child);
				}

				// add to todo list only if not already processed before
				if(!visited.contains(child)) {
					todo.add(child);
				}
			}
			visited.add(parent);
		}

		return versionSpace;
	}

	public static void main(String[] args) throws Exception{
		ToStringRenderer.getInstance().setRenderer(new ManchesterOWLSyntaxOWLObjectRendererImpl());

		OWLOntologyManager man = OWLManager.createOWLOntologyManager();
		OWLOntology ont = man.loadOntologyFromOntologyDocument(new File("../examples/father.owl"));

		AbstractReasonerComponent reasoner = new ClosedWorldReasoner(new OWLAPIOntology(ont));
		reasoner.init();

		ComplexityModel complexityModel = new HybridComplexityModel(
				new ClassExpressionLengthComplexityModel(7),
				new ClassExpressionDepthComplexityModel(2)
		);

		ComplexityBoundedOperator op = new ComplexityBoundedOperatorALC(reasoner);
		op.setComplexityModel(complexityModel);
		op.init();

		VersionSpaceGenerator generator = new ComplexityBoundedVersionSpaceGenerator(op);
		VersionSpace g = generator.generate();

		GraphUtils.writeGraphML(g, "/tmp/versionspace.graphml");

//		Set<OWLClassExpression> refinements = new TreeSet<>();
//		refinements.add(man.getOWLDataFactory().getOWLThing());
//
//		Set<OWLClassExpression> tmp;
//		do {
//			tmp = new HashSet<>();
//			for (OWLClassExpression ce : refinements) {
//				tmp.addAll(op.refine(ce));
//			}
//		} while(!tmp.isEmpty() && refinements.addAll(tmp));
//
//		System.out.println("#Refinements: " + refinements.size());
//		for (OWLClassExpression ref : refinements) {
//			System.out.println(ref);
//		}
	}
}
