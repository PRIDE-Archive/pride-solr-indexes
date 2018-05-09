package uk.ac.ebi.pride.solr.indexes.pride.repository;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.solr.core.query.result.FacetAndHighlightPage;
import org.springframework.data.solr.core.query.result.HighlightPage;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.pride.archive.dataprovider.param.CvParamProvider;
import uk.ac.ebi.pride.archive.dataprovider.param.DefaultCvParam;
import uk.ac.ebi.pride.archive.dataprovider.user.ContactProvider;
import uk.ac.ebi.pride.archive.dataprovider.user.DefaultContact;
import uk.ac.ebi.pride.archive.dataprovider.utils.TitleConstants;
import uk.ac.ebi.pride.archive.repo.project.*;
import uk.ac.ebi.pride.solr.indexes.pride.config.ArchiveOracleConfig;
import uk.ac.ebi.pride.solr.indexes.pride.config.SolrLocalhostTestConfiguration;
import uk.ac.ebi.pride.solr.indexes.pride.model.PrideProjectField;
import uk.ac.ebi.pride.solr.indexes.pride.model.PrideSolrDataset;
import uk.ac.ebi.pride.solr.indexes.pride.utils.RequiresSolrServer;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This code is licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * ==Overview==
 * <p>
 * This class
 * <p>
 * Created by ypriverol (ypriverol@gmail.com) on 27/04/2018.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = {SolrLocalhostTestConfiguration.class, ArchiveOracleConfig.class})
public class OracleSolrRepositoryTests {

    public static RequiresSolrServer requiresRunningServer = RequiresSolrServer.onLocalhost();

    @Autowired
    SolrProjectRepository repository;

    @Autowired
    ProjectRepository oracleRepository;

    @PostConstruct
    public void setTest(){
        repository.deleteAll();
        insertDatasetsFromOracle();
    }

    public void insertDatasetsFromOracle(){
        List<PrideSolrDataset> projects = new ArrayList<>();
        oracleRepository.findAll().forEach(x -> {
            PrideSolrDataset solrProject = new PrideSolrDataset();
            solrProject.setAccession(x.getAccession());
            solrProject.setTitle(x.getTitle());
            solrProject.setProjectDescription(x.getProjectDescription());
            solrProject.setDataProcessingProtocol(x.getDataProcessingProtocol());
            solrProject.setSampleProcessingProtocol(x.getSampleProcessingProtocol());

            //Capture keywords
            solrProject.setKeywords(Arrays.asList(x.getKeywords().split(",")));
            solrProject.setProjectTags(x.getProjectTags().stream().map(ProjectTag::getTag).collect(Collectors.toList()));

            //Capture datasets
            solrProject.setSubmissionDate(x.getSubmissionDate());
            solrProject.setPublicationDate(x.getPublicationDate());
            solrProject.setUpdatedDate(x.getUpdateDate());

            // Affiliations
            List<ContactProvider> labHead = new ArrayList<>();
            x.getLabHeads().stream().forEach(contactX -> {
                labHead.add(new DefaultContact(TitleConstants.fromString(contactX.getTitle().getTitle()), contactX.getFirstName(), contactX.getLastName(), contactX.getId().toString(), contactX.getAffiliation(),contactX.getEmail(), "US", "")); }
            );
            solrProject.setLabPIFromContacts(labHead);

            ContactProvider submitter = new DefaultContact(TitleConstants.fromString(x.getSubmitter().getTitle().getTitle()), x.getSubmitter().getFirstName(), x.getSubmitter().getLastName(), x.getSubmitter().getId().toString(), x.getSubmitter().getAffiliation(),x.getSubmitter().getEmail(), "US", "");
            solrProject.setSubmittersFromContacts(submitter);

            List<ContactProvider> contacts= labHead;
            contacts.add(submitter);
            solrProject.setAffiliationsFromContacts(contacts);

            // Get Instruments
            List<CvParamProvider> instruments =  new ArrayList<>();
            x.getInstruments().stream().forEach(instrumet -> {
                instruments.add(new DefaultCvParam(instrumet.getCvLabel(), instrumet.getAccession(), instrumet.getName(), instrumet.getValue()));
            });
            solrProject.setInstrumentsFromCvParam(instruments);

            // References
            List<String> references = x.getReferences().stream().map(Reference::getReferenceLine).collect(Collectors.toList());
            solrProject.setReferences(new HashSet<>(references));

            //Modifications
            solrProject.setIdentifiedPTMStringsFromCvParam(x.getPtms().stream().map(xPTM -> new DefaultCvParam(xPTM.getCvLabel(), xPTM.getAccession(), xPTM.getName(), xPTM.getValue())).collect(Collectors.toList()));

            //Get software information
            Collection<ProjectSoftwareCvParam> softwaresOld = x.getSoftware();
            solrProject.setSoftwaresFromCvParam(softwaresOld.stream().map(xSoft -> new DefaultCvParam(xSoft.getAccession(), xSoft.getName())).collect(Collectors.toList()));

            //Add Additional Attributes
            solrProject.addAdditionalAttributesFromCvParams(x.getExperimentTypes().stream().map(xType -> new DefaultCvParam(xType.getAccession(), xType.getName())).collect(Collectors.toList()));
            solrProject.addQuantificationMethodsFromCvParams(x.getQuantificationMethods().stream().map(xQuant -> new DefaultCvParam(xQuant.getAccession(), xQuant.getName())).collect(Collectors.toList()));

            Collection<ProjectSampleCvParam> samples = x.getSamples();

            solrProject.setSampleAttributes(samples.stream().map(xSample -> new DefaultCvParam(xSample.getAccession(), xSample.getName())).collect(Collectors.toList()));
            projects.add(solrProject);
        });
        repository.saveAll(projects);
    }

    /** Finds all entries using a single request. */
    @Test
    public void findAll() {
        repository.findAll().forEach(x -> System.out.println("Accession: " + x.getAccession() + " -- Title: " + x.getTitle()));
    }

    /** Finds all entries using a single request by Cursor */
    @Test
    public void findCursorAll() {
        repository.findAllUsingCursor().forEachRemaining(x -> System.out.println("Accession: " + x.getAccession() + " -- Title: " + x.getTitle()));
    }

    /** Find the dataset for specific accession **/
    @Test
    public void findByAccession(){
        PrideSolrDataset x = repository.findByAccession("PXD000001");
        System.out.println("Accession: " + x.getAccession() + " -- Title: " + x.getTitle());
    }

    @Test
    public void findProjectsByKey(){
        HighlightPage<PrideSolrDataset> page = repository.findByKeyword("*:*", null, PrideProjectField.ACCESSION, new PageRequest(1, 10));
        page.forEach(System.out::println);
    }

    @Test
    public void findProjectsByKeyFacet(){

        Page<PrideSolrDataset> page = repository.findAllIgnoreCase(new PageRequest(0, 10));

        // Print all the projects search
        page.forEach( x-> {
            System.out.println(x.toString());
        });

//        //Print Facets
//        page.getAllFacets().stream().forEach(x -> {
//            System.out.println(x.getContent());
//        });
    }

    @Test
    public void finadAllAndaFacet(){
        Page<PrideSolrDataset> projects = repository.findAllIgnoreCase(new PageRequest(1, 10));
        Assert.assertEquals(((FacetAndHighlightPage) projects).getFacetResultPage(PrideProjectField.PROJECT_PUBLICATION_DATE).getContent().size(), 1);
//        Assert.assertEquals(projects.getFacetResultPage(PrideProjectField.PROJECT_SUBMISSION_DATE).getContent().size(), 1);
//        Assert.assertEquals(projects.getFacetResultPage(PrideProjectField.PROJECT_UPDATED_DATE).getContent().size(), 1);
    }
}