/*
 * Copyright 2014-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.ebi.pride.solr.indexes.repository;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.CoreDocument;
import edu.stanford.nlp.pipeline.CoreSentence;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.simple.Sentence;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SuggesterResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.FacetParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.solr.core.SolrTemplate;
import org.springframework.data.solr.core.query.FacetOptions;
import org.springframework.data.solr.core.query.FacetQuery;
import org.springframework.data.solr.core.query.HighlightOptions;
import org.springframework.data.solr.core.query.HighlightQuery;
import org.springframework.data.solr.core.query.SimpleFacetQuery;
import org.springframework.data.solr.core.query.SimpleHighlightQuery;
import org.springframework.data.solr.core.query.SimpleQuery;
import org.springframework.data.solr.core.query.SimpleStringCriteria;
import org.springframework.data.solr.core.query.result.Cursor;
import org.springframework.data.solr.core.query.result.FacetPage;
import org.springframework.data.solr.core.query.result.HighlightPage;
import org.springframework.util.MultiValueMap;
import uk.ac.ebi.pride.solr.commons.DataPair;
import uk.ac.ebi.pride.solr.commons.PrideProjectField;
import uk.ac.ebi.pride.solr.commons.PrideProjectFieldEnum;
import uk.ac.ebi.pride.solr.commons.PrideSolrProject;
import uk.ac.ebi.pride.solr.commons.Utils.PrideSolrConstants;
import uk.ac.ebi.pride.solr.indexes.utils.QueryBuilder;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Implementation of {@link SolrProjectRepositoryCustom}.
 *
 * @author ypriverol
 * @version $Id$
 *
 */
class SolrProjectRepositoryImpl implements SolrProjectRepositoryCustom {

	/** Solr Template **/
	final SolrTemplate solrTemplate;

	@Autowired
	public SolrProjectRepositoryImpl(SolrTemplate solrTemplate) {
		this.solrTemplate = solrTemplate;
	}

	@Override
	public Cursor<PrideSolrProject> findAllUsingCursor() {
		return solrTemplate.queryForCursor(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, new SimpleQuery("*:*").addSort(Sort.by("id")), PrideSolrProject.class);
	}

	/**
	 * This method will search Pride projects using the keywords defined by pride and will filter but only a set of fields
	 * @param page
	 * @return
	 */
	@Override
	public HighlightPage<PrideSolrProject> findByKeyword(List<String> keywords, MultiValueMap<String, String> filters, Pageable page, String dateGap) {
		HighlightQuery highlightQuery = new SimpleHighlightQuery();
		highlightQuery = (HighlightQuery) QueryBuilder.keywordORQuery(highlightQuery, keywords, filters, dateGap);
		highlightQuery.setPageRequest(page);

		HighlightOptions highlightOptions = new HighlightOptions();
		Arrays.stream(PrideProjectFieldEnum.values()).filter(PrideProjectFieldEnum::getHighlight).forEach(x-> highlightOptions.addField(x.getValue()));
		highlightOptions.setFragsize(PrideSolrConstants.DEFAULT_FRAGMENT_SIZE);
		highlightOptions.setNrSnipplets(PrideSolrConstants.DEFAULT_NRSNIPPLETS_SIZE);
		highlightQuery.setHighlightOptions(highlightOptions);


		if(highlightQuery.getSort() == null)
			highlightQuery.addSort(new Sort(Sort.Direction.DESC, PrideProjectFieldEnum.ACCESSION.getValue()));

		return solrTemplate.queryForHighlightPage(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, highlightQuery , PrideSolrProject.class);
	}

	@Override
	public FacetPage<PrideSolrProject> findFacetByKeyword(List<String> keywords, MultiValueMap<String, String> filters, Pageable page, Pageable facetPage, String dateGap) {

		FacetQuery facetQuery = new SimpleFacetQuery();
		facetQuery = (FacetQuery) QueryBuilder.keywordORQuery(facetQuery, keywords, filters, dateGap);
		facetQuery.setPageRequest(page);

		FacetOptions facetOptions = new FacetOptions();
		facetOptions.setPageable(facetPage);
		PrideSolrConstants.AllowedDateGapConstants gapConstants = PrideSolrConstants.AllowedDateGapConstants.findByString(dateGap);
		if( gapConstants != PrideSolrConstants.AllowedDateGapConstants.UNKONWN){
			Arrays.asList(PrideProjectFieldEnum.values())
					.stream()
					.filter(PrideProjectFieldEnum::getFacet)
					.forEach(facetField -> {
						if(facetField.getType() == PrideSolrConstants.ConstantsSolrTypes.DATE){
							try {
								Date startDate = new SimpleDateFormat("yyyy-MM-dd").parse("2004-01-01");
								FacetOptions.FieldWithDateRangeParameters dateRange = new FacetOptions.FieldWithDateRangeParameters(facetField.getValue(), startDate, new Date(), gapConstants.value);
								dateRange.setHardEnd(true).setInclude(FacetParams.FacetRangeInclude.LOWER).setOther(FacetParams.FacetRangeOther.BEFORE);
								facetOptions.addFacetByRange(dateRange);
							} catch (ParseException e) {
								e.printStackTrace();
							}
						}else{
							facetOptions.addFacetOnField(facetField.getValue());
						}

					});
		}else{
			Arrays.asList(PrideProjectFieldEnum.values())
					.stream()
					.filter(PrideProjectFieldEnum::getFacet)
					.forEach(facetField -> facetOptions.addFacetOnField(facetField.getValue()));
			facetOptions.setFacetMinCount(1);
		}


		facetQuery.setFacetOptions(facetOptions);


		if(facetQuery.getSort() == null)
			facetQuery.addSort(new Sort(Sort.Direction.DESC, PrideProjectFieldEnum.ACCESSION.getValue()));

		return solrTemplate.queryForFacetPage(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, facetQuery , PrideSolrProject.class);
	}

	@Override
	public FacetPage<PrideSolrProject> findAllFacetIgnoreCase(Pageable pageRequest) {
		FacetOptions facets = new FacetOptions().setPageable(pageRequest);
		for(PrideProjectFieldEnum field: PrideProjectFieldEnum.values())
			if(field.getFacet())
				facets.addFacetOnField(field.getValue());
		FacetQuery query = new SimpleFacetQuery(new SimpleStringCriteria("*:*")).setFacetOptions(facets);
		return solrTemplate.queryForFacetPage(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, query, PrideSolrProject.class);
	}

	@Override
	public Map<String,Double> findMoreLikeThisIds(String accession, Integer pageSize, Integer page) {
		Map<String, Double> solrProjectIds = new HashMap<>();
		SolrQuery queryParams = new SolrQuery();
		queryParams.setRows(pageSize);
		queryParams.setStart(page*pageSize);
		queryParams.setMoreLikeThisFields(PrideProjectField.PROJECT_TILE, PrideProjectField.PROJECT_DESCRIPTION, PrideProjectField.INSTRUMENTS, PrideProjectField.ORGANISM,
				PrideProjectField.ORGANISM_PART, PrideProjectField.DISEASES, PrideProjectField.PROJECT_IDENTIFIED_PTM_STRING, PrideProjectField.PROJECT_DATA_PROTOCOL,
				PrideProjectField.PROJECT_SAMPLE_PROTOCOL, PrideProjectField.TEXT);
		queryParams.setQuery(PrideProjectField.ACCESSION + ":" + accession);
		try {
			QueryResponse q = solrTemplate.getSolrClient().query(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, queryParams);
			q.getMoreLikeThis().asShallowMap().values()
					.stream()
					.forEach(x -> {
						for (SolrDocument doc : x) {
							solrProjectIds.put((String) doc.getFirstValue(PrideProjectField.ID), ((Float) doc.getFirstValue("score")).doubleValue());
						}
					});
		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		}
		return solrProjectIds;
	}

	@Override
	public List<String> findAutoComplete(String keyword){
		SolrQuery solrQuery = new SolrQuery();
		solrQuery.setRequestHandler("/suggest");
		solrQuery.setRows(10);
		solrQuery.set("suggest", "true");
		solrQuery.set("suggest.dictionary", "textSuggester");
		solrQuery.set("suggest.q", keyword);

		/*solrQuery.setRequestHandler("/suggestone");
		solrQuery.setRows(10);
		solrQuery.set("suggest", "true");
		solrQuery.set("suggest.dictionary", "textSuggesterone");
		solrQuery.set("suggest.q", keyword);*/

		try {
			QueryResponse re = solrTemplate.getSolrClient().query(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, solrQuery);
			SuggesterResponse sr = re.getSuggesterResponse();
			//return sr.getSuggestedTerms();

			//Get Suggestions from Solr
			Map<String, List<String>> autocompleValues = sr.getSuggestedTerms();
			List<String> terms = autocompleValues.entrySet().stream()
					.flatMap(x->x.getValue().stream())
					.collect(Collectors.toList());

			/*Lemmatize the keywords to reduce them to base forms.
			This helps in eliminating duplicate results like "proteome","proteomes" etc
			 */
			String[] keywordsArr = keyword.split(" ");
			int index=0;
			for(String word : keywordsArr){
				keywordsArr[index++] = new Sentence(word).lemma(0);
			}

			//This tree provides sorting is based on sentence/string length
			Set<String> finalSuggestionsSet = new TreeSet<String>((o1, o2) -> {
				int diff = o1.length()-o2.length();
				if(diff==0){
					return o1.compareTo(o2);
				}else {
					return o1.length() - o2.length();
				}
			});

			//NLP code for POS tagging and extracting required words from solr suggestion texts
			List<String> partsOfSpeechTagsList = Arrays.asList("NN","NNS","NNP","NNPS","JJ");
			HashMap<String,Integer> suggestPOSWordsMap = new HashMap<String, Integer>();
			Properties props = new Properties();
			// set the list of annotators to run
			props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
			// build pipeline
			StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
			for(String suggestionText : terms){
				suggestPOSWordsMap = new HashMap<String, Integer>();
				//Remove the highlighting provided by solr.
				String plainText = suggestionText.replace("<b>","").replace("</b>","").toLowerCase();
				// create a document object
				CoreDocument document = new CoreDocument(plainText);
				// annnotate the document
				pipeline.annotate(document);
				//Build a map with suggestion words and their count of occurence in the text.
				for(CoreSentence sentence : document.sentences()){
					for(CoreLabel token : sentence.tokens()){
						if( partsOfSpeechTagsList.contains(token.tag()) && token.value().length()>3) {
							if(suggestPOSWordsMap.containsKey(token.value())){
								suggestPOSWordsMap.put(token.value(),suggestPOSWordsMap.get(token.value())+1);
							}else{
								suggestPOSWordsMap.put(token.value(),1);
							}
						}
					}
				}

				/*Sort the suggestions according to their frequency
				DataPair class is "comparable"
				*/
				TreeSet<DataPair> orderedSuggestionsPOSSet = new TreeSet<DataPair>();
				for(String key:suggestPOSWordsMap.keySet()){
					orderedSuggestionsPOSSet.add(new DataPair(suggestPOSWordsMap.get(key),key));
				}


				/*Split the scentence into words
				  Extract the Highlighted(...<b>...</b>...) words
				  add words to a set to eliminate dupes
				 */

				Set<String> suggestionsSet = Arrays.asList(suggestionText.split(" "))
						.stream().filter( word -> word.contains("<b>") && word.contains("</b>"))
						.map(word -> {
							word = word.replace("<b>","").replace("</b>","").replaceAll("[^a-zA-Z0-9-]+","").toLowerCase();
							return new Sentence(word).lemma(0);
						})
						.collect(Collectors.toSet());

				int count=0;
				//to order words in suggestion based on their positions in the user provided search text;
				List<String> orderedList = new LinkedList<String>();
				for(String word:keywordsArr){
					Iterator<String> iterator = suggestionsSet.iterator();
					while(iterator.hasNext()){
						String item = iterator.next();
						if(item.contains(word)){
							orderedList.add(item);
							iterator.remove();
						}
					}
				}
				//add all reamining items at the end of the ordered list
				if(suggestionsSet.size()>0){
					orderedList.addAll(suggestionsSet);
				}

				for(DataPair suggestionDataPair : orderedSuggestionsPOSSet){
					//Build suggestions by merging the keywords and the identified POS-tagged word
					//Each scentence will have atmost count(3) number of entries
					if (!orderedList.contains(suggestionDataPair.getName()) && count < 3) {
						finalSuggestionsSet.add(orderedList.stream().collect(Collectors.joining(" ")) + " " + suggestionDataPair.getName());
						count++;
					} else if (!suggestionText.contains(" ")) {
						finalSuggestionsSet.add(orderedList.stream().collect(Collectors.joining()));
					}
				}
			}


			//limit results to 10
			return finalSuggestionsSet.stream().filter(scentence -> !scentence.contains("http") && !scentence.contains("ftp") && !scentence.contains("href") && scentence.length() <= 40).limit(10).collect(Collectors.toList());

		} catch (SolrServerException | IOException e) {
			e.printStackTrace();
		}
		return Collections.emptyList();
	}

	@Override
	public Set<String> findProjectAccessionsWithEmptyPeptideSequencesOrProteinIdentifications() throws IOException, SolrServerException {
		return getAccessionsOrIdsFromSolr(PrideProjectField.ACCESSION,
				"-" + PrideProjectField.PROTEIN_IDENTIFICATIONS + ":[\"\" TO *]",
				"-" + PrideProjectField.PEPTIDE_SEQUENCES + ":[\"\" TO *]");
	}

	@Override
	public Set<String> findProjectAccessionsWithEmptyFileNames() throws IOException, SolrServerException {
		return getAccessionsOrIdsFromSolr(PrideProjectField.ACCESSION, "-" + PrideProjectField.PROJECT_FILE_NAMES + ":[\"\" TO *]");
	}

	@Override
	public Set<String> findAllAccessions() throws IOException, SolrServerException {
		return getAccessionsOrIdsFromSolr(PrideProjectField.ACCESSION, "*:*");
	}

	@Override
	public Set<String> findAllIds() throws IOException, SolrServerException {
		return getAccessionsOrIdsFromSolr(PrideProjectField.ID, "*:*");
	}

	private Set<String> getAccessionsOrIdsFromSolr(String field, String... queries) throws IOException, SolrServerException {
		Set<String> prjAccessionsOrIds = new HashSet<>();
		SolrQuery solrQuery = new SolrQuery();
		for (String query : queries) {
			solrQuery.setQuery(query);
		}

		int start = 0;
		int cnt = 1000;

		solrQuery.setRows(cnt);
		solrQuery.setFields(field);
		while (true) {
			solrQuery.setStart(start);
			SolrDocumentList solrDocuments = solrTemplate.getSolrClient().query(PrideProjectField.PRIDE_PROJECTS_COLLECTION_NAME, solrQuery).getResults();
			if (solrDocuments.size() == 0) {
				break;
			}
			solrDocuments.stream().forEach(solrDocument -> {
				prjAccessionsOrIds.add((String) solrDocument.getFieldValue(field));

			});
			start += cnt;
		}
		return prjAccessionsOrIds;
	}


}
