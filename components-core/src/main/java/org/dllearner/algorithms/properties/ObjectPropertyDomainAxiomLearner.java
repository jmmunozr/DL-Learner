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

package org.dllearner.algorithms.properties;

import java.util.Set;

import org.dllearner.core.AbstractAxiomLearningAlgorithm;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.kb.SparqlEndpointKS;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;

@ComponentAnn(name="objectproperty domain axiom learner", shortName="opldomain", version=0.1)
public class ObjectPropertyDomainAxiomLearner extends AbstractAxiomLearningAlgorithm<OWLObjectPropertyDomainAxiom, OWLIndividual> {
	
private static final Logger logger = LoggerFactory.getLogger(ObjectPropertyDomainAxiomLearner.class);
	
	private OWLObjectProperty propertyToDescribe;
	
	public ObjectPropertyDomainAxiomLearner(SparqlEndpointKS ks){
		this.ks = ks;
		super.posExamplesQueryTemplate = new ParameterizedSparqlString("SELECT DISTINCT ?s WHERE {?s a ?type}");
		super.negExamplesQueryTemplate = new ParameterizedSparqlString("SELECT DISTINCT ?s WHERE {?s ?p ?o. FILTER NOT EXISTS{?s a ?type}}");
	}
	
	public OWLObjectProperty getPropertyToDescribe() {
		return propertyToDescribe;
	}

	public void setPropertyToDescribe(OWLObjectProperty propertyToDescribe) {
		this.propertyToDescribe = propertyToDescribe;
	}
	
	/* (non-Javadoc)
	 * @see org.dllearner.core.AbstractAxiomLearningAlgorithm#getExistingAxioms()
	 */
	@Override
	protected void getExistingAxioms() {
		OWLClassExpression existingDomain = reasoner.getDomain(propertyToDescribe);
		if(existingDomain != null){
			existingAxioms.add(df.getOWLObjectPropertyDomainAxiom(propertyToDescribe, existingDomain));
			if(reasoner.isPrepared()){
				if(reasoner.getClassHierarchy().contains(existingDomain)){
					for(OWLClassExpression sup : reasoner.getClassHierarchy().getSuperClasses(existingDomain)){
						existingAxioms.add(df.getOWLObjectPropertyDomainAxiom(propertyToDescribe, existingDomain));
						logger.info("Existing domain(inferred): " + sup);
					}
				}
				
			}
		}
	}
	
	protected void learnAxioms() {
		if(!forceSPARQL_1_0_Mode && ks.supportsSPARQL_1_1()){
			runSingleQueryMode();
		} else {
			runSPARQL1_0_Mode();
		}
	};
	
	private void runSingleQueryMode(){
		
		String query = String.format("SELECT (COUNT(DISTINCT ?s) AS ?cnt) WHERE {?s <%s> ?o.}", propertyToDescribe.toStringID());
		ResultSet rs = executeSelectQuery(query);
		int nrOfSubjects = rs.next().getLiteral("cnt").getInt();
		
		query = String.format("SELECT ?type (COUNT(DISTINCT ?s) AS ?cnt) WHERE {?s <%s> ?o. ?s a ?type.} GROUP BY ?type", 
				propertyToDescribe.toStringID());
		rs = executeSelectQuery(query);
		QuerySolution qs;
		while(rs.hasNext()){
			qs = rs.next();
			OWLClass domain = df.getOWLClass(IRI.create((qs.getResource("type").getURI())));
			int cnt = qs.getLiteral("cnt").getInt();
			if(!domain.isOWLThing()){
				currentlyBestAxioms.add(new EvaluatedAxiom<OWLObjectPropertyDomainAxiom>(df.getOWLObjectPropertyDomainAxiom(propertyToDescribe, domain), computeScore(nrOfSubjects, cnt)));
			}
		}
	}
	
	private void runSPARQL1_0_Mode() {
		workingModel = ModelFactory.createDefaultModel();
		int limit = 1000;
		int offset = 0;
		String baseQuery  = "CONSTRUCT {?s a ?type.} WHERE {?s <%s> ?o. ?s a ?type.} LIMIT %d OFFSET %d";
		String query = String.format(baseQuery, propertyToDescribe.toStringID(), limit, offset);
		Model newModel = executeConstructQuery(query);
		while(!terminationCriteriaSatisfied() && newModel.size() != 0){
			workingModel.add(newModel);
			// get number of distinct subjects
			query = "SELECT (COUNT(DISTINCT ?s) AS ?all) WHERE {?s a ?type.}";
			ResultSet rs = executeSelectQuery(query, workingModel);
			QuerySolution qs;
			int all = 1;
			while (rs.hasNext()) {
				qs = rs.next();
				all = qs.getLiteral("all").getInt();
			}
			
			// get class and number of instances
			query = "SELECT ?type (COUNT(DISTINCT ?s) AS ?cnt) WHERE {?s a ?type.} GROUP BY ?type ORDER BY DESC(?cnt)";
			rs = executeSelectQuery(query, workingModel);
			
			if (all > 0) {
				currentlyBestAxioms.clear();
				while(rs.hasNext()){
					qs = rs.next();
					Resource type = qs.get("type").asResource();
					//omit owl:Thing as trivial domain
					if(type.equals(OWL.Thing)){
						continue;
					}
					currentlyBestAxioms.add(new EvaluatedAxiom<OWLObjectPropertyDomainAxiom>(
							df.getOWLObjectPropertyDomainAxiom(propertyToDescribe, df.getOWLClass(IRI.create(type.getURI()))),
							computeScore(all, qs.get("cnt").asLiteral().getInt())));
				}
				
			}
			offset += limit;
			query = String.format(baseQuery, propertyToDescribe.toStringID(), limit, offset);
			newModel = executeConstructQuery(query);
			fillWithInference(newModel);
		}
	}
	
	private void fillWithInference(Model model){
		Model additionalModel = ModelFactory.createDefaultModel();
		if(reasoner.isPrepared()){
			for(StmtIterator iter = model.listStatements(null, RDF.type, (RDFNode)null); iter.hasNext();){
				Statement st = iter.next();
				OWLClass cls = df.getOWLClass(IRI.create(st.getObject().asResource().getURI()));
				if(reasoner.getClassHierarchy().contains(cls)){
					for(OWLClassExpression sup : reasoner.getClassHierarchy().getSuperClasses(cls)){
						additionalModel.add(st.getSubject(), st.getPredicate(), model.createResource(sup.toString()));
					}
				}
			}
		}
		model.add(additionalModel);
	}
	
	@Override
	public Set<OWLIndividual> getPositiveExamples(EvaluatedAxiom<OWLObjectPropertyDomainAxiom> evAxiom) {
		OWLObjectPropertyDomainAxiom axiom = evAxiom.getAxiom();
		posExamplesQueryTemplate.setIri("type", axiom.getDomain().asOWLClass().toStringID());
		return super.getPositiveExamples(evAxiom);
	}
	
	@Override
	public Set<OWLIndividual> getNegativeExamples(EvaluatedAxiom<OWLObjectPropertyDomainAxiom> evAxiom) {
		OWLObjectPropertyDomainAxiom axiom = evAxiom.getAxiom();
		negExamplesQueryTemplate.setIri("type", axiom.getDomain().asOWLClass().toStringID());
		return super.getNegativeExamples(evAxiom);
	}
	
}
