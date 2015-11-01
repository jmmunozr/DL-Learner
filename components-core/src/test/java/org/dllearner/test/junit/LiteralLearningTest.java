package org.dllearner.test.junit;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Level;
import org.dllearner.algorithms.celoe.CELOE;
import org.dllearner.core.AbstractKnowledgeSource;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentInitException;
import org.dllearner.kb.OWLAPIOntology;
import org.dllearner.learningproblems.PosNegLPStandard;
import org.dllearner.reasoning.ClosedWorldReasoner;
import org.dllearner.reasoning.OWLAPIReasoner;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.owl.DLSyntaxObjectRenderer;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.dllearner.core.StringRenderer;
import org.dllearner.core.StringRenderer.Rendering;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.vocab.OWLFacet;

import uk.ac.manchester.cs.owl.owlapi.OWLDataFactoryImpl;
import uk.ac.manchester.cs.owl.owlapi.OWLLiteralImplInteger;

import com.clarkparsia.owlapiv3.XSD;
import com.google.common.collect.Sets;

public final class LiteralLearningTest {
	static final String NUMBERS = "http://dl-learner.org/test/numbers#";
	static final String DOUBLES = "http://dl-learner.org/test/doubles#";
	static final String SHORTS = "http://dl-learner.org/test/shorts#";
	static final String FLOATS = "http://dl-learner.org/test/floats#";
	static final String DATES = "http://dl-learner.org/test/dates#";
	static final String DATETIMES = "http://dl-learner.org/test/datetimes#";
	static final String MONTHS = "http://dl-learner.org/test/months#";
	
	
	static final String NUMBERS_OWL = "../test/literals/numbers.owl";
	static final String DOUBLES_OWL = "../test/literals/doubles.owl";
	static final String SHORTS_OWL = "../test/literals/shorts.owl";
	static final String FLOATS_OWL = "../test/literals/floats.owl";
	static final String DATES_OWL = "../test/literals/dates.owl";
	static final String DATETIMES_OWL = "../test/literals/datetimes.owl";
	static final String MONTHS_OWL = "../test/literals/months-noz.owl";
	
	private class TestRunner {
		public AbstractReasonerComponent[] rcs;
		private String prefix;
		private File file;
		private PrefixManager pm;
		public AbstractKnowledgeSource ks;
		public OWLDataFactory df;
		private OWLClassExpression target;
		private OWLDatatype restrictionType;
		private int maxNrOfSplits;
		TestRunner(String prefix, String owlfile, OWLDatatype restrictionType, int maxNrOfSplits) throws OWLOntologyCreationException, ComponentInitException {
			this.prefix = prefix;
			this.restrictionType = restrictionType;
			this.maxNrOfSplits = maxNrOfSplits;
			org.apache.log4j.Logger.getLogger("org.dllearner").setLevel(Level.DEBUG);
//			org.apache.log4j.Logger.getLogger(CELOE.class).setLevel(Level.DEBUG);

//			StringRenderer.setRenderer(Rendering.MANCHESTER_SYNTAX);
//			StringRenderer.setRenderer(Rendering.DL_SYNTAX);

			File file = new File(owlfile);
			OWLOntology ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(file);
			df = new OWLDataFactoryImpl();
			pm = new DefaultPrefixManager(prefix);
			ks = new OWLAPIOntology(ontology);
			ks.init();
		}
		TestRunner(String prefix, String owlfile, OWLDatatype restrictionType) throws OWLOntologyCreationException, ComponentInitException {
			this(prefix, owlfile, restrictionType, 12);
		}
		public void run() throws ComponentInitException {
			Set<OWLIndividual> positiveExamples = new TreeSet<OWLIndividual>();
			positiveExamples.add(df.getOWLNamedIndividual("N1", pm));
			positiveExamples.add(df.getOWLNamedIndividual("N2", pm));
			positiveExamples.add(df.getOWLNamedIndividual("N3", pm));
			
			Set<OWLIndividual> negativeExamples = new TreeSet<OWLIndividual>();
			negativeExamples.add(df.getOWLNamedIndividual("N100", pm));
			negativeExamples.add(df.getOWLNamedIndividual("N102", pm));
			negativeExamples.add(df.getOWLNamedIndividual("N104", pm));
			
			for(AbstractReasonerComponent rc : rcs) {
				PosNegLPStandard lp = new PosNegLPStandard(rc);
				lp.setPositiveExamples(positiveExamples);
				lp.setNegativeExamples(negativeExamples);
				lp.init();
				
				RhoDRDown op = new RhoDRDown();
				op.setUseTimeDatatypes(true);
				op.setUseNumericDatatypes(true);
				op.setReasoner(rc);
				op.setMaxNrOfSplits(maxNrOfSplits);
				op.init();
				
				CELOE alg = new CELOE(lp, rc);
				alg.setMaxClassDescriptionTests(1000);
				alg.setMaxExecutionTimeInSeconds(0);
				alg.setOperator(op);
				alg.init();
				
				alg.start();
				OWLClassExpression soln = alg.getCurrentlyBestDescription();

				assertTrue(soln.getNNF().equals(target));
				
			}
		}
		public void setSingleRestrictionTarget(OWLFacet facetType, String solution) {
			this.target = df.getOWLDataSomeValuesFrom(
					df.getOWLDataProperty(IRI.create(prefix + "value")),
					df.getOWLDatatypeRestriction(
							restrictionType,
							df.getOWLFacetRestriction(
									facetType,
									df.getOWLLiteral(solution, restrictionType))));
		}

		public void setDualRestrictionTarget(String minSolution, String maxSolution) {
			this.target = df.getOWLDataSomeValuesFrom(
					df.getOWLDataProperty(IRI.create(prefix + "value")),
					df.getOWLDatatypeRestriction(
							restrictionType,
							Sets.newHashSet(
									df.getOWLFacetRestriction(
											OWLFacet.MAX_INCLUSIVE,
											df.getOWLLiteral(maxSolution, restrictionType)
											),
											df.getOWLFacetRestriction(
													OWLFacet.MIN_INCLUSIVE,
													df.getOWLLiteral(minSolution, restrictionType))
									)));
		}
		public void setReasoners(AbstractReasonerComponent... rcs) throws ComponentInitException {
			this.rcs = rcs;
			for(AbstractReasonerComponent rc : this.rcs) {
				rc.init();
			}
		}
	}
	
	private void genericNumericTypeTest (String prefix, String owlfile, OWLDatatype restrictionType, String solution) throws OWLOntologyCreationException, ComponentInitException {
		TestRunner runner = new TestRunner(prefix, owlfile, restrictionType);
		
		runner.setSingleRestrictionTarget(OWLFacet.MAX_INCLUSIVE, solution);
		
		ClosedWorldReasoner cwr = new ClosedWorldReasoner(runner.ks);
		OWLAPIReasoner oar = new OWLAPIReasoner(runner.ks);
		runner.setReasoners(cwr, oar);
		
		runner.run();

	}

	@Test
	public void doubleTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		genericNumericTypeTest(DOUBLES, DOUBLES_OWL, (new OWLDataFactoryImpl()).getDoubleOWLDatatype(), "9.5");
	}
	
	@Test
	public void numericTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		genericNumericTypeTest(NUMBERS, NUMBERS_OWL, (new OWLDataFactoryImpl()).getIntegerOWLDatatype(), "55");
	}

	@Test
	public void shortTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		genericNumericTypeTest(SHORTS, SHORTS_OWL, XSD.SHORT, "9");
	}
	
	@Test
	public void floatTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		genericNumericTypeTest(FLOATS, FLOATS_OWL, (new OWLDataFactoryImpl()).getFloatOWLDatatype(), "9.5");
	}
	
	@Test
	public void dateTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		// E+: 1970-10-22, 1970-11-27, 1971-09-24
		// E-: 1970-01-05, 2002-03-24, 2002-09-27
		// T : 1970-10-22 <= x <= 1971-09-24
		TestRunner runner = new TestRunner(DATES, DATES_OWL, XSD.DATE);
		
		runner.setDualRestrictionTarget("1970-01-22", "1971-09-24");
		
		ClosedWorldReasoner cwr = new ClosedWorldReasoner(runner.ks);
		OWLAPIReasoner oar = new OWLAPIReasoner(runner.ks); // upload fixed version of Pellet and confirm that it works
		runner.setReasoners(cwr , oar);
		
		runner.run();
	}
	
	@Test
	public void datetimeTypeTest () throws ComponentInitException, OWLOntologyCreationException {
		// E+: 1970-10-22, 1970-11-27, 1971-09-24
		// E-: 1970-01-05, 2002-03-24, 2002-09-27
		// T : 1970-10-22 <= x <= 1971-09-24
		TestRunner runner = new TestRunner(DATETIMES, DATETIMES_OWL, XSD.DATE_TIME);
		
		runner.setDualRestrictionTarget("1970-01-22T08:10:10", "1971-09-24T02:22:22");
		
		ClosedWorldReasoner cwr = new ClosedWorldReasoner(runner.ks);
//		OWLAPIReasoner oar = new OWLAPIReasoner(runner.ks);
//		oar.setReasonerImplementation(ReasonerImplementation.HERMIT);
		runner.setReasoners(cwr /*, oar */); // TODO: figure out why this crashes on ci and @Patrick
		
		runner.run();
	}

	@Test
	public void gMonthTypeTest () throws OWLOntologyCreationException, ComponentInitException {
		// TODO Pellet does not support any time zone
		TestRunner runner = new TestRunner(MONTHS, MONTHS_OWL, XSD.G_MONTH, 12);
		
		runner.setDualRestrictionTarget("--03", "--05");
		
		ClosedWorldReasoner cwr = new ClosedWorldReasoner(runner.ks);
		OWLAPIReasoner oar = new OWLAPIReasoner(runner.ks); // upload fixed version of Pellet and confirm that it works
		runner.setReasoners(cwr , oar);
		
		runner.run();
	}
	
	@Test
	public void literalComparisonTest () {
		OWLLiteralImplInteger lit1 = new OWLLiteralImplInteger(50, XSD.INTEGER);
		OWLLiteralImplInteger lit2 = new OWLLiteralImplInteger(100, XSD.INTEGER);
		
		int diffImpl = lit1.compareTo(lit2);
		System.out.println(diffImpl);
		
		int diffValue = Integer.compare(lit1.parseInteger(), lit2.parseInteger());
		System.out.println(diffValue);
		
		System.out.println("Same sorting:" + (Math.signum(diffImpl) == Math.signum(diffValue)));
		
	}
	
	
}