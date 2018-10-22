package com.coremedia.blueprint.cae.search;

import com.coremedia.blueprint.cae.search.facet.FacetResult;
import com.coremedia.blueprint.cae.search.facet.FacetValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The SearchResultBean is a search engine independent representation of a search result.
 *
 * @cm.template.api
 */
public class SearchResultBean {

  private SearchQueryBean searchQuery = null;
  private List<?> hits = Collections.emptyList();
  private long numHits = 0;
  private FacetResult facetResult = new FacetResult();
  private String spellSuggestion = "";
  private Map<Object, Map<String, List<String>>> highlightingResults = Collections.emptyMap();
  private List<ValueAndCount> autocompleteSuggestions;
  private int hitsPerPage = 10;

  /**
   * Returns the original {@link SearchQueryBean query} for this result.
   *
   * @return the original {@link SearchQueryBean query} for this result.
   */
  public SearchQueryBean getSearchQuery() {
    return searchQuery;
  }

  /**
   * Sets the original {@link SearchQueryBean query} for this result.
   *
   * @param searchQuery the original {@link SearchQueryBean query} for this result.
   */
  public void setSearchQuery(SearchQueryBean searchQuery) {
    this.searchQuery = searchQuery;
  }

  /**
   * Returns the hits returned by the search engine
   *
   * @return the hits returned by the search engine
   * @cm.template.api
   */
  public List<?> getHits() {
    return hits;
  }

  /**
   * Sets the hits returned by the search engine
   *
   * @param hits the hits returned by the search engine
   */
  public void setHits(List<?> hits) {
    this.hits = hits;
  }

  /**
   * Returns the total number of hits
   *
   * @return the total number of hits
   * @cm.template.api
   */
  public long getNumHits() {
    return numHits;
  }

  /**
   * Sets the total number of hits
   *
   * @param numHits the total number of hits
   */
  public void setNumHits(long numHits) {
    this.numHits = numHits;
  }

  /**
   * Returns faceting results as map from facet name to list of facet values.
   *
   * <p>This is the same as {@link #getFacetResult()}.{@link FacetResult#getFacets() getFacets()}
   * but with a different return type {@code Map<String, List<}{@link ValueAndCount}{@code >} instead
   * of {@code Map<String, Collection<}{@link FacetValue}{@code >}.
   *
   * @return the facets for this search
   * @deprecated since 1810, use {@link #getFacetResult()}.{@link FacetResult#getFacets() getFacets()} instead
   */
  @Deprecated
  public Map<String, List<ValueAndCount>> getFacets() {
    return Maps.transformValues(facetResult.getFacets(), ImmutableList::copyOf);
  }

  /**
   * Sets the faceting results as map from facet name to list of facet values.
   *
   * @param facets the facets for this search
   * @deprecated since 1810, construct a {@link FacetResult} and set it with {@link #setFacetResult(FacetResult)}
   *             instead
   */
  @Deprecated
  public void setFacets(Map<String, List<ValueAndCount>> facets) {
    ImmutableMap.Builder<String, Collection<FacetValue>> builder = ImmutableMap.builder();
    for (Map.Entry<String, List<ValueAndCount>> entry : facets.entrySet()) {
      String facet = entry.getKey();
      List<FacetValue> facetValues = entry.getValue().stream()
        .map(valueAndCount -> new FacetValue(facet, valueAndCount.getValue(), valueAndCount.getCount()))
        .collect(Collectors.toList());
      builder.put(facet, facetValues);
    }
    this.facetResult = new FacetResult(builder.build());
  }

  /**
   * Returns faceting results.
   *
   * @return faceting results
   * @since 1810
   * @cm.template.api
   */
  public FacetResult getFacetResult() {
    return facetResult;
  }

  /**
   * Sets faceting results.
   *
   * @param facetResult faceting results
   * @since 1810
   */
  public void setFacetResult(FacetResult facetResult) {
    this.facetResult = facetResult;
  }

  /**
   * Returns the spell suggestion for this query
   *
   * @return the spell suggestion for this query
   */
  public String getSpellSuggestion() {
    return spellSuggestion;
  }

  /**
   * Sets the spell suggestion for this query
   *
   * @param spellSuggestion the spell suggestion for this query
   */
  public void setSpellSuggestion(String spellSuggestion) {
    this.spellSuggestion = spellSuggestion;
  }

  /**
   * Returns the search engine supplied highlighted search results (if any). Follows the Solr convention and
   * returns a map where the key is the id of the result item and the value is a map where the key is a field
   * in the result item and the value is a list of Strings with highlighting embedded.
   *
   * @return a map where the key is the id of the result item and the value is a map where the key is a field
   * in the result item and the value is a list of Strings with highlighting embedded.
   */
  public Map<Object, Map<String, List<String>>> getHighlightingResults() {
    return highlightingResults;
  }

  /**
   * Sets the search engine supplied highlighted search results.
   *
   * @param highlightingResults the search engine supplied highlighted search results.
   */
  public void setHighlightingResults(Map<Object, Map<String, List<String>>> highlightingResults) {
    this.highlightingResults = highlightingResults;
  }

  /**
   * Returns a single highlighted item as map from highlightingResults
   *
   * @param key the object as key for the highlightingResults map
   * @return a map with the highlighted results for the given object
   * @cm.template.api
   */
  public Map<String, List<String>> getHighlightingResultsItem(Object key) {
    return this.highlightingResults.get(key);
  }

  /**
   * Returns the auto-complete suggestions for this query.
   *
   * @return the auto-complete suggestions for this query
   */
  public List<ValueAndCount> getAutocompleteSuggestions() {
    return autocompleteSuggestions;
  }

  /**
   * Sets the auto-complete suggestions for this query.
   */
  public void setAutocompleteSuggestions(List<ValueAndCount> autocompleteSuggestions) {
    this.autocompleteSuggestions = autocompleteSuggestions;
  }

  /**
   * Return the number of hits per page
   *
   * @return the number of hits per page
   * @cm.template.api
   */
  public int getHitsPerPage() {
    return hitsPerPage;
  }

  /**
   * Sets the number of hits per page
   *
   * @param hitsPerPage the number of hits per page
   */
  public void setHitsPerPage(int hitsPerPage) {
    this.hitsPerPage = hitsPerPage;
  }
}
