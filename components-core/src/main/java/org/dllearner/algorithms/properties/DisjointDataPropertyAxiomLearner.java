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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dllearner.core.AbstractAxiomLearningAlgorithm;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.config.DataPropertyEditor;
import org.dllearner.core.owl.DatatypeProperty;
import org.dllearner.core.owl.DisjointDatatypePropertyAxiom;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SPARQLTasks;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.learningproblems.AxiomScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;

@ComponentAnn(name="disjoint dataproperty axiom learner", shortName="dpldisjoint", version=0.1)
public class DisjointDataPropertyAxiomLearner extends AbstractAxiomLearningAlgorithm {
	
	private static final Logger logger = LoggerFactory.getLogger(ObjectPropertyDomainAxiomLearner.class);
	
	@ConfigOption(name="propertyToDescribe", description="", propertyEditorClass=DataPropertyEditor.class)
	private DatatypeProperty propertyToDescribe;
	
	private Set<DatatypeProperty> allDataProperties;
	
	public DisjointDataPropertyAxiomLearner(SparqlEndpointKS ks){
		this.ks = ks;
	}
	
	public DatatypeProperty getPropertyToDescribe() {
		return propertyToDescribe;
	}

	public void setPropertyToDescribe(DatatypeProperty propertyToDescribe) {
		this.propertyToDescribe = propertyToDescribe;
	}
	
	@Override
	public void start() {
		logger.info("Start learning...");
		startTime = System.currentTimeMillis();
		fetchedRows = 0;
		currentlyBestAxioms = new ArrayList<EvaluatedAxiom>();
		
		//TODO
		
		//at first get all existing dataproperties in knowledgebase
		allDataProperties = new SPARQLTasks(ks.getEndpoint()).getAllDataProperties();
		allDataProperties.remove(propertyToDescribe);
		
		if(ks.supportsSPARQL_1_1()){
			runSPARQL1_1_Mode();
		} else {
			runSPARQL1_0_Mode();
		}
		
		logger.info("...finished in {}ms.", (System.currentTimeMillis()-startTime));
	}
	
	private void runSPARQL1_0_Mode() {
		Model model = ModelFactory.createDefaultModel();
		int limit = 1000;
		int offset = 0;
		String baseQuery  = "CONSTRUCT {?s ?p ?o.} WHERE {?s <%s> ?o. ?s ?p ?o.} LIMIT %d OFFSET %d";
		String query = String.format(baseQuery, propertyToDescribe.getName(), limit, offset);
		Model newModel = executeConstructQuery(query);
		Map<DatatypeProperty, Integer> result = new HashMap<DatatypeProperty, Integer>();
		while(!terminationCriteriaSatisfied() && newModel.size() != 0){
			model.add(newModel);
			query = "SELECT ?p (COUNT(?s) AS ?count) WHERE {?s ?p ?o.} GROUP BY ?p";
			
			DatatypeProperty prop;
			Integer oldCnt;
			ResultSet rs = executeSelectQuery(query, model);
			QuerySolution qs;
			while(rs.hasNext()){
				qs = rs.next();
				prop = new DatatypeProperty(qs.getResource("p").getURI());
				int newCnt = qs.getLiteral("count").getInt();
				oldCnt = result.get(prop);
				if(oldCnt == null){
					oldCnt = Integer.valueOf(newCnt);
				}
				result.put(prop, oldCnt);
				qs.getLiteral("count").getInt();
			}
			if(!result.isEmpty()){
				currentlyBestAxioms = buildAxioms(result, allDataProperties);
			}
			
			
			offset += limit;
			query = String.format(baseQuery, propertyToDescribe.getName(), propertyToDescribe.getName(), limit, offset);
			newModel = executeConstructQuery(query);
		}
		
	}
	
	private void runSPARQL1_1_Mode() {
		//get properties and how often they occur
		int limit = 1000;
		int offset = 0;
		String queryTemplate = "SELECT ?p (COUNT(?s) as ?count) WHERE {?s ?p ?o." +
		"{SELECT ?s ?o WHERE {?s <%s> ?o.} LIMIT %d OFFSET %d}" +
		"}";
		String query;
		Map<DatatypeProperty, Integer> result = new HashMap<DatatypeProperty, Integer>();
		DatatypeProperty prop;
		Integer oldCnt;
		boolean repeat = true;
		
		ResultSet rs = null;
		while(!terminationCriteriaSatisfied() && repeat){
			query = String.format(queryTemplate, propertyToDescribe, limit, offset);
			rs = executeSelectQuery(query);
			QuerySolution qs;
			repeat = false;
			while(rs.hasNext()){
				qs = rs.next();
				prop = new DatatypeProperty(qs.getResource("p").getURI());
				int newCnt = qs.getLiteral("count").getInt();
				oldCnt = result.get(prop);
				if(oldCnt == null){
					oldCnt = Integer.valueOf(newCnt);
				} else {
					oldCnt += newCnt;
				}
				result.put(prop, oldCnt);
				repeat = true;
			}
			if(!result.isEmpty()){
				currentlyBestAxioms = buildAxioms(result, allDataProperties);
				offset += 1000;
			}
		}
		
	}

	@Override
	public List<EvaluatedAxiom> getCurrentlyBestEvaluatedAxioms() {
		return currentlyBestAxioms;
	}

	private List<EvaluatedAxiom> buildAxioms(Map<DatatypeProperty, Integer> property2Count, Set<DatatypeProperty> allProperties){
		List<EvaluatedAxiom> axioms = new ArrayList<EvaluatedAxiom>();
		Integer all = property2Count.get(propertyToDescribe);
		property2Count.remove(propertyToDescribe);
		
		EvaluatedAxiom evalAxiom;
		//first create disjoint axioms with properties which not occur and give score of 1
		for(DatatypeProperty p : allProperties){
			evalAxiom = new EvaluatedAxiom(new DisjointDatatypePropertyAxiom(propertyToDescribe, p),
					new AxiomScore(1));
			axioms.add(evalAxiom);
		}
		
		//second create disjoint axioms with other properties and score 1 - (#occurence/#all)
		for(Entry<DatatypeProperty, Integer> entry : sortByValues(property2Count)){
			evalAxiom = new EvaluatedAxiom(new DisjointDatatypePropertyAxiom(propertyToDescribe, entry.getKey()),
					new AxiomScore(1 - (entry.getValue() / (double)all)));
			axioms.add(evalAxiom);
		}
		
		property2Count.put(propertyToDescribe, all);
		return axioms;
	}
	
	public static void main(String[] args) throws Exception{
		DisjointDataPropertyAxiomLearner l = new DisjointDataPropertyAxiomLearner(new SparqlEndpointKS(SparqlEndpoint.getEndpointDBpediaLiveAKSW()));
		l.setPropertyToDescribe(new DatatypeProperty("http://dbpedia.org/ontology/position"));
		l.setMaxExecutionTimeInSeconds(20);
		l.init();
		l.start();
		System.out.println(l.getCurrentlyBestEvaluatedAxioms(5));
	}

}
