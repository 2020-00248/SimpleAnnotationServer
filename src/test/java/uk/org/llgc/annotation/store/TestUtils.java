package uk.org.llgc.annotation.store;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Before;
import org.junit.After;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import uk.org.llgc.annotation.store.exceptions.IDConflictException;
import uk.org.llgc.annotation.store.adapters.StoreAdapter;
import uk.org.llgc.annotation.store.AnnotationUtils;
import uk.org.llgc.annotation.store.exceptions.IDConflictException;
import uk.org.llgc.annotation.store.encoders.Encoder;

import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.Lang;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.query.* ;

import org.openrdf.repository.http.HTTPRepository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;

import java.util.Properties;


public class TestUtils {
	protected static Logger _logger = LogManager.getLogger(TestUtils.class.getName()); 
	protected AnnotationUtils _annotationUtils = null;
	protected StoreAdapter _store = null;
	//@Rule
	protected File _testFolder = null;
	protected Map<String,String> _props = null;

	public TestUtils() throws IOException {
		this(null);
	}

	public TestUtils(final Encoder pEncoder) throws IOException {
		_testFolder = new File(new File(getClass().getResource("/").toString()),"tmp");
		_annotationUtils = new AnnotationUtils(new File(getClass().getResource("/contexts").getFile()), pEncoder);

		Properties tProps = new Properties();
		tProps.load(new FileInputStream(new File(getClass().getResource("/test.properties").getFile())));
		_props = new HashMap<String,String>();
		for (String tKey : tProps.stringPropertyNames()) {
			_props.put(tKey, tProps.getProperty(tKey));
		}
	}

   public void setup() throws IOException {
		if (_props.get("store").equals("jena")) {
			File tDataDir = new File(_testFolder, "data");
			tDataDir.mkdirs();
			_props.put("data_dir",tDataDir.getPath());
		}	

		StoreConfig tConfig = new StoreConfig(_props);
		StoreConfig.initConfig(tConfig);
		_store = StoreConfig.getConfig().getStore();
	}

	public String getAnnoId(final Model pModel) {
		Iterator<Resource> tSubjects = pModel.listSubjects();
		Resource tAnnoId = null;
		while (tSubjects.hasNext()) {
			Resource tSubject = tSubjects.next();
			if (tSubject.getURI() != null && tSubject.getURI().contains("http://")) {
				tAnnoId = tSubject;
				break;
			}
		}

		return tAnnoId.getURI();
	}

   public void tearDown() throws IOException {
		if (_props.get("store").equals("jena")) {
			File tDataDir = new File(_testFolder, "data");
			this.delete(tDataDir);
		}	else {
			try {
				HTTPRepository tRepo = new HTTPRepository(_props.get("repo_url"));
				RepositoryConnection tConn = tRepo.getConnection();
				tConn.clear();
			} catch (RepositoryException tExcpt) {
				tExcpt.printStackTrace();
				throw new IOException(tExcpt.getMessage());
			}
		}
	}

	protected void delete(File f) throws IOException {
		if (f.isDirectory()) {
			for (File c : f.listFiles()) {
				this.delete(c);
			}	
		}
		if (!f.delete()) {
			throw new IOException("Failed to delete file: " + f);
		}	
	}

	protected void testAnnotation(final Model pModel, final String pValue, final String pTarget) {
		String tQuery = "PREFIX oa: <http://www.w3.org/ns/oa#> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX cnt: <http://www.w3.org/2011/content#> select ?content ?uri ?fragement where { ?annoId oa:hasTarget ?target . ?target oa:hasSource ?uri . ?target oa:hasSelector ?fragmentCont . ?fragmentCont rdf:value ?fragement . ?annoId oa:hasBody ?body . ?body cnt:chars ?content }";
		this.queryAnnotation(pModel,tQuery, pValue, pTarget);
	}

	protected void testAnnotation(final Model pModel, final String pId, final String pValue, final String pTarget) {
		String tQuery = "PREFIX oa: <http://www.w3.org/ns/oa#> PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX cnt: <http://www.w3.org/2011/content#> select ?content ?uri ?fragement where { <$id> oa:hasTarget ?target . ?target oa:hasSource ?uri . ?target oa:hasSelector ?fragmentCont . ?fragmentCont rdf:value ?fragement . <$id> oa:hasBody ?body . ?body cnt:chars ?content }".replaceAll("\\$id",pId);
		this.queryAnnotation(pModel, tQuery, pValue, pTarget);
	}	

	protected ResultSet queryAnnotation(final Model pModel, final String pQuery, final String pValue, final String pTarget) {
		Query query = QueryFactory.create(pQuery) ;
		ResultSetRewindable results = null;
		boolean tFound = false;
		try (QueryExecution qexec = QueryExecutionFactory.create(query,pModel)) {
		
			results = ResultSetFactory.copyResults(qexec.execSelect());
			for ( ; results.hasNext() ; ) {
				tFound = true;
				QuerySolution soln = results.nextSolution() ;
				assertEquals("Content doesn't match.", "<p>" + pValue + "</p>", soln.getLiteral("content").toString());

				String tURI = soln.getResource("uri").toString() + "#" + soln.getLiteral("fragement");
				assertEquals("Target doesn't match", pTarget, tURI);
			}
		}
		results.reset();
		assertTrue("Expected to find annotation but found no results.", tFound);
		return results;
	}

	protected Statement matchesValue(final Model pModel, final Resource pResource, final String pProp, final String pValue) {
		StmtIterator tResults = pModel.listStatements(pResource, pModel.createProperty(pProp), (Resource)null);
		assertTrue("Missing " + pProp + " for resource " + pResource.getURI(), tResults.hasNext());
		Statement tResult = tResults.nextStatement();
		assertEquals("Value mismatch", pValue, tResult.getString());

		return tResult;
	}

	protected Statement matchesValue(final Model pModel, final Resource pResource, final String pProp, final Resource pValue) {
		StmtIterator tResults = pModel.listStatements(pResource, pModel.createProperty(pProp), (Resource)null);
		assertTrue("Missing " + pProp + " for resource " + pResource.getURI(), tResults.hasNext());
		Statement tResult = tResults.nextStatement();
		assertEquals("Value mismatch", pValue.getURI(), tResult.getResource().getURI());

		return tResult;
	}

}
