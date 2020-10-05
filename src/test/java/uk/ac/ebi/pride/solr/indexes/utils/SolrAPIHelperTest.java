package uk.ac.ebi.pride.solr.indexes.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.ac.ebi.pride.solr.commons.PrideProjectField;
import uk.ac.ebi.pride.solr.commons.SolrAPIHelper;

/**
 This class will test simple functions to interact with the Solr API.

 @author ypriverol
 @version $Id$

 */
public class SolrAPIHelperTest {


    SolrAPIHelper solrAPIHelper;

    @Before
    public void setUp() throws Exception {
        solrAPIHelper = SolrAPIHelper.getInstance("http://localhost:8983");
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void deleteCollection() throws Exception {
        String collection = "pride_projects";
        if(solrAPIHelper.deleteCollection(collection)){
            System.out.println("Collection -- pride_projects has been deleted -- ");
        }
        if(solrAPIHelper.deleteConfigSet(collection)){
            System.out.println("ConfigSet -- pride_projects has been deleted -- ");
        }
    }

    @Test
    public void createCollection() throws Exception {
        String collection = "pride_projects";
        if(solrAPIHelper.createCollection(collection, 2, 2, 2)){
            System.out.println("Collection -- pride_projects has been created  -- ");
        }
    }

    @Test
    public void listConfigSets() throws Exception {
        String collection = "pride_projects";
        if(solrAPIHelper.containsConfigSet(collection)){
            System.out.println("The following configset has been found in the server --- " + collection);
        }
    }

    @Test
    public void updateFieldType() throws Exception {
        String collection = "pride_projects";
        if(solrAPIHelper.updateFieldType(collection, PrideProjectField.PROJECT_TAGS_FACET, "string", true, null)){
            System.out.println("The field -- " + PrideProjectField.PROJECT_TAGS_FACET + " -- has been changed in the type -- " + "string" + " -- in collection -- " + collection);
        }
    }

    @Test
    public void getSchemaByCollection() throws Exception {
        String collection = "pride_projects";
        String schemaString = solrAPIHelper.getSchemaByCollection(collection);
        System.out.println(schemaString);

    }

    @Test
    public void refinePrideSolrProjectsSchema() throws Exception {
        String collection = "pride_projects";
        if(solrAPIHelper.refinePrideSolrProjectsSchema(collection)){
            System.out.println("Collection -- pride_projects has been refined -- ");
        }
    }

}