package uk.ac.ebi.pride.solr.indexes.repository;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.solr.repository.Query;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.pride.solr.commons.PrideProjectField;
import uk.ac.ebi.pride.solr.commons.PrideSolrProject;


/**
 * Solr Crud Repository is helping the implementation to handle the {@link uk.ac.ebi.pride.solr.commons.PrideSolrProject} with the basics functions suchs as
 * insert/update/delete.
 *
 * @author Yasset Perez-Riverol
 * @version $Id$
 */

@Repository
public interface SolrProjectRepository extends SolrProjectRepositoryCustom, CrudRepository<PrideSolrProject, String> {

    /**
     * Return a {@link uk.ac.ebi.pride.solr.commons.PrideSolrProject} for a given Accession
     *
     * @param accession PRoject accession
     * @return PrideSolrProject
     */
    @Query(PrideProjectField.ACCESSION + ":?0")
    PrideSolrProject findByAccession(String accession);

    /**
     * This function returns all the {@link PrideSolrProject} in the registry including the following facets:
     *  - project publication date
     *  - project submission date
     *  - project updated date
     *  - project tags
     *  - project keywords
     *  - project instruments
     *  - project affiliations
     *  - project experimental factors
     *  - project organisms
     *  - project organism_parts
     *  - project diseases
     * @param pageable {@link Pageable}
     * @return
     */
    @Query(value = "*:*")
    Page<PrideSolrProject> findAllIgnoreCase(Pageable pageable);

}
