package uk.ac.ebi.pride.solr.indexes.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.solr.core.query.result.FacetPage;
import org.springframework.data.solr.core.query.result.HighlightPage;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.pride.solr.commons.PrideSolrProject;
import uk.ac.ebi.pride.solr.commons.Utils.StringUtils;
import uk.ac.ebi.pride.solr.indexes.repository.SolrProjectRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This service allows to make updates and save of the Pride Projects and should be used instead of pure repository.
 */
@Service
@Slf4j
public class SolrProjectService {

    final SolrProjectRepository repository;

    @Autowired
    public SolrProjectService(SolrProjectRepository repository) {
        this.repository = repository;
    }


    /**
     * This method check first that not project with same accession is in the collection and then insert it the new
     * project. If the project is in Solr then service do not save it. For updating the project please check the following
     * method
     *
     * @param project
     * @return
     */
    public PrideSolrProject save(PrideSolrProject project) {
        PrideSolrProject prideSolrProject = repository.findByAccession(project.getAccession());
        if (prideSolrProject == null) {
            repository.save(project);
            log.info("A new project has been saved in Solr index -- " + project.getAccession());
        } else
            log.info("A project with same accession has been found in the Solr index, you MUST use update -- " + project.getAccession());
        return project;
    }

    /**
     * This method check first that not project with same accession is in the collection and then insert it the new
     * project. If the project is in Solr then service do not save it. For updating the project please check the following
     * method
     *
     * @param project
     * @return
     */

    public PrideSolrProject update(PrideSolrProject project) {
        project = repository.save(project);
        return project;
    }

    /**
     * This function inserts a project in the Solr Database but if the project already exist in the database, it will update the record
     *
     * @param project {@link PrideSolrProject}
     * @return PrideSolrProject
     */

    public PrideSolrProject upsert(PrideSolrProject project) {
        PrideSolrProject solrProject = repository.findByAccession(project.getAccession());
        if (solrProject == null) {
            project = repository.save(project);
            log.info("A new project has been saved in Solr index -- " + project.getAccession());
        } else {
            project.setId((String) solrProject.getId());
            project = repository.save(project);
            log.info("project has been Inserted or updated in MongoDB with accession -- " + project.getAccession());
        }
        return project;
    }

    /**
     * Find all the Projects in the Repository
     *
     * @return
     */
    public Iterable<PrideSolrProject> findAll() {
        return repository.findAll();
    }

    /*
     * Delete a particular project from solr
     * */

    public void deleteProjectById(String id) {
        repository.deleteById(id);
    }

    /**
     * Delete all the Projects in the Repository.
     */

    public void deleteAll() {
        repository.deleteAll();
    }

    /**
     * Parallel save of the projects in the collection. This needs to be tested
     *
     * @param projects
     */

    public void saveAll(List<PrideSolrProject> projects) {
        repository.saveAll(projects);
    }

    public Iterator<PrideSolrProject> findAllUsingCursor() {
        return repository.findAllUsingCursor();
    }

    public PrideSolrProject findByAccession(String accession) {
        return repository.findByAccession(accession);
    }

    /**
     * The keywords are all the words we would like to find datasets for them into the Solr index. Filter Query should have the following structure:
     * field1==queryValue, field1==QueryValue
     *
     * @param keywords    List of keywords to be found in the search
     * @param filterQuery filter Query in the following format ield1:queryValue, field1:QueryValue
     * @param pageRequest Page Request.
     * @return List of datasets Pagination
     */
    public HighlightPage<PrideSolrProject> findByKeyword(List<String> keywords, String filterQuery, PageRequest pageRequest, String dateGap) {

        MultiValueMap<String, String> filters = StringUtils.parseFilterParameters(filterQuery);
        return repository.findByKeyword(keywords, filters, pageRequest, dateGap);
    }

    public Page<PrideSolrProject> findAllIgnoreCase(PageRequest pageRequest) {
        return repository.findAllIgnoreCase(pageRequest);

    }

    public FacetPage<PrideSolrProject> findAllFacetIgnoreCase(Pageable pageRequest) {
        return repository.findAllFacetIgnoreCase(pageRequest);
    }

    public FacetPage<PrideSolrProject> findFacetByKeyword(List<String> keywords, String filterQuery, PageRequest pageRequest, PageRequest facetPage, String gap) {
        MultiValueMap<String, String> filters = StringUtils.parseFilterParameters(filterQuery);
        return repository.findFacetByKeyword(keywords, filters, pageRequest, facetPage, gap);
    }

    /**
     * This method retrieve a datasets that are similar to an specific dataset. The method remove the requested dataset
     * from the list.
     *
     * @param accession Project Accession
     * @return List of Similar projects.
     */
    public List<PrideSolrProject> findSimilarProjects(String accession, Integer pageSize, Integer page) {
        Map<String, Double> ids = repository.findMoreLikeThisIds(accession, pageSize, page);
        List<PrideSolrProject> similarDatasets = new ArrayList<>();
        Iterable<PrideSolrProject> itDatasets = repository.findAllById(ids.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
        if (itDatasets != null) {
            itDatasets.forEach(x -> {
                if (!x.getAccession().equalsIgnoreCase(accession)) {
                    x.setScore(new Float(ids.get(x.getId())));
                    similarDatasets.add(x);
                }
            });
        }
        return similarDatasets;
    }

    /**
     * Find the Autocomplete values for an specific query.
     *
     * @param keyword
     * @return
     */
    public List<String> findAutoComplete(String keyword) {
        return repository.findAutoComplete(keyword);
    }

    public Set<String> findProjectAccessionsWithEmptyPeptideSequencesOrProteinIdentifications() throws IOException, SolrServerException {
        return repository.findProjectAccessionsWithEmptyPeptideSequencesOrProteinIdentifications();
    }

    public Set<String> findProjectAccessionsWithEmptyFileNames() throws IOException, SolrServerException {
        return repository.findProjectAccessionsWithEmptyFileNames();
    }

    public Set<String> findAllAccessions() throws IOException, SolrServerException {
        return repository.findAllAccessions();
    }

    public Set<String> findAllIds() throws IOException, SolrServerException {
        return repository.findAllIds();
    }
}
