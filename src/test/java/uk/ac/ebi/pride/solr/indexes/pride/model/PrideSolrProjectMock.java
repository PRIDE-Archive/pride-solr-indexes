package uk.ac.ebi.pride.solr.indexes.pride.model;

import org.junit.Test;
import org.mockito.Mockito;

/**
 * Mock a PRIDE Solr Project Object.
 *
 * @author ypriverol
 * @version $Id$
 */
public class PrideSolrProjectMock {

    @Test
    public void prideSolrMock(){

        PrideSolrDataset mockPrideSolrProject = Mockito.mock(PrideSolrDataset.class);
        System.out.println(mockPrideSolrProject);
    }

}