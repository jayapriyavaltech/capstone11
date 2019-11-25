package com.coremedia.blueprint.studio.rest.taxonomies;

import com.coremedia.blueprint.taxonomies.Taxonomy;
import com.coremedia.blueprint.taxonomies.TaxonomyNode;
import com.coremedia.blueprint.taxonomies.TaxonomyNodeList;
import com.coremedia.blueprint.taxonomies.TaxonomyResolver;
import com.coremedia.blueprint.taxonomies.TaxonomyUtil;
import com.coremedia.blueprint.taxonomies.semantic.SemanticStrategy;
import com.coremedia.blueprint.taxonomies.semantic.Suggestion;
import com.coremedia.blueprint.taxonomies.semantic.Suggestions;
import com.coremedia.rest.exception.WebApplicationException;
import com.coremedia.rest.linking.AbstractLinkingResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "taxonomies", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaxonomyResource extends AbstractLinkingResource {
  private static final Logger LOG = LoggerFactory.getLogger(TaxonomyResource.class);
  private static final String ID = "id";
  private static final String MAX = "max";
  private static final String SITE = "site";
  private static final String RELOAD = "reload";
  private static final String TEXT = "text";
  private static final String OFFSET = "offset";
  private static final String LENGTH = "length";
  private static final String TAXONOMY_ID = "taxonomyId";
  private static final String NODE_REF = "nodeRef";
  private static final String NODE_REFS = "nodeRefs";
  private static final String TARGET_NODE_REF = "targetNodeRef";
  private static final String DEFAULT_NAME = "defaultName";

  private TaxonomyResolver strategyResolver;

  private List<SemanticStrategy> semanticStrategies;
  private Map<String, SemanticStrategy> semanticStrategyById = new HashMap<>();

  public TaxonomyResource(TaxonomyResolver strategyResolver, List<SemanticStrategy> semanticStrategies) {
    this.strategyResolver = strategyResolver;
    this.semanticStrategies = semanticStrategies;
  }

  @GetMapping("find")
  public TaxonomyNodeList find(@RequestParam(value = SITE, required = false) String siteId,
                               @RequestParam(value = TAXONOMY_ID, required = false) String taxonomyId,
                               @RequestParam(value = TEXT, required = false) String text) {
    TaxonomyNodeList list = new TaxonomyNodeList();
    try {
      if (taxonomyId == null || taxonomyId.length() == 0) {
        for (Taxonomy strategy : getTaxonomiesForAdministration(siteId)) {
          TaxonomyNodeList strategyHits = strategy.find(text);
          if (strategyHits.getNodes() != null) {
            list.getNodes().addAll(strategyHits.getNodes());
          }
        }
      }
      else {
        Taxonomy taxonomy = getTaxonomy(siteId, taxonomyId);
        TaxonomyNodeList strategyHits = taxonomy.find(text);
        if (strategyHits.getNodes() != null) {
          list.getNodes().addAll(strategyHits.getNodes());
        }
      }
    } catch (Exception e) {
      LOG.error("Search failed for text " + text, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return list;
  }

  @GetMapping("roots")
  public TaxonomyNodeList getRoots(@RequestParam(value = SITE, required = false) String siteId, @RequestParam(RELOAD) boolean reload) {
    try {
      List<TaxonomyNode> roots = new ArrayList<>();
      if (reload) {
        strategyResolver.reload();
      }
      for (Taxonomy strategy : getTaxonomiesForAdministration(siteId)) {
        roots.add(strategy.getRoot());
      }
      TaxonomyNodeList list = new TaxonomyNodeList(roots);
      list.sortByName();
      return list;
    } catch (Exception e) {
      LOG.error("roots failed.", e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("root")
  public TaxonomyNode getRoot(@RequestParam(value = SITE, required = false) String siteId, @RequestParam(TAXONOMY_ID) String taxonomyId) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      return strategy.getRoot();
    } catch (Exception e) {
      LOG.error("root failed.", e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("parent")
  public TaxonomyNode getParent(@RequestParam(value = SITE, required = false) String siteId,
                                @RequestParam(TAXONOMY_ID) String taxonomyId,
                                @RequestParam(NODE_REF) String ref) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      return strategy.getParent(ref);
    } catch (Exception e) {
      LOG.error("parent failed for " + ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("node")
  public TaxonomyNode getNode(@RequestParam(value = SITE, required = false) String siteId,
                              @RequestParam(TAXONOMY_ID) String taxonomyId,
                              @RequestParam(NODE_REF) String ref) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      return strategy.getNodeByRef(ref);
    } catch (Exception e) {
      LOG.error("getNode failed for " + ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @PostMapping("bulkmove")
  public TaxonomyNodeList bulkMove(@RequestParam(value = SITE, required = false) String siteId,
                                   @RequestParam(TAXONOMY_ID) String taxonomyId,
                                   @RequestParam(NODE_REFS) String refs,
                                   @RequestParam(TARGET_NODE_REF) String targetRef) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      TaxonomyNodeList result = new TaxonomyNodeList();

      String[] nodeReferences = refs.split(",");
      for (String nodeReference : nodeReferences) {
        TaxonomyNode node = strategy.getNodeByRef(nodeReference);
        TaxonomyNode targetNode = strategy.getNodeByRef(targetRef);
        result.getNodes().add(strategy.moveNode(node, targetNode));
      }

      return result;
    } catch (Exception e) {
      LOG.error("move node failed for " + refs, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  @PostMapping("bulkdelete")
  public TaxonomyNode bulkDelete(@RequestParam(value = SITE, required = false) String siteId,
                                 @RequestParam(TAXONOMY_ID) String taxonomyId,
                                 @RequestParam(NODE_REFS) String refs) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      TaxonomyNode parent = null;
      String[] nodeReferences = refs.split(",");
      for (String nodeReference : nodeReferences) {
        TaxonomyNode node = strategy.getNodeByRef(nodeReference);
        parent = strategy.delete(node);
      }
      return parent;
    } catch (Exception e) {
      LOG.error("delete failed for " + refs, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  @PostMapping("bulklinks")
  public List<Object> bulkReferrers(@RequestParam(value = SITE, required = false) String siteId,
                                    @RequestParam(TAXONOMY_ID) String taxonomyId,
                                    @RequestParam(NODE_REFS) String refs) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      List<Object> result = new ArrayList<>();

      String[] nodeReferences = refs.split(",");
      for (String nodeReference : nodeReferences) {
        TaxonomyNode node = strategy.getNodeByRef(nodeReference);
        result.addAll(strategy.getLinks(node, true));
      }

      return result;
    } catch (Exception e) {
      LOG.error("bulkreferrers node failed for " + refs, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @PostMapping("bulkstronglinks")
  public List<Object> bulkStrongLinks(@RequestParam(value = SITE, required = false) String siteId,
                                      @RequestParam(TAXONOMY_ID) String taxonomyId,
                                      @RequestParam(NODE_REFS) String refs) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      List<Object> result = new ArrayList<>();

      String[] nodeReferences = refs.split(",");
      for (String nodeReference : nodeReferences) {
        TaxonomyNode node = strategy.getNodeByRef(nodeReference);
        result.addAll(strategy.getStrongLinks(node, true));
      }

      return result;
    } catch (Exception e) {
      LOG.error("bulkreferrers node failed for " + refs, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("path")
  public TaxonomyNode getPath(@RequestParam(value = SITE, required = false) String siteId,
                              @RequestParam(TAXONOMY_ID) String taxonomyId,
                              @RequestParam(NODE_REF) String ref) {
    try {
      if (taxonomyId == null) {
        LOG.warn("path called without taxonomyId!");
        return null;
      }
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      TaxonomyNode node = strategy.getNodeByRef(ref);
      return strategy.getPath(node);
    } catch (Exception e) {
      LOG.error("getPath failed for " + ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("children")
  public TaxonomyNodeList getChildren(@RequestParam(value = SITE, required = false) String siteId,
                                      @RequestParam(TAXONOMY_ID) String taxonomyId,
                                      @RequestParam(NODE_REF) String ref,
                                      @RequestParam(value = OFFSET, required = false) Integer offset,
                                      @RequestParam(value = LENGTH, required = false) Integer length) {
    try {
      if (taxonomyId == null) {
        LOG.warn("children called without taxonomyId!");
        return null;
      }
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      //can happen when the taxonomy root node has been deleted but the manager is still open
      TaxonomyNode node = (ref == null)
              ? strategy.getRoot()
              : strategy.getNodeByRef(ref);
      TaxonomyNodeList children = strategy.getChildren(node, (offset == null) ? 0 : offset, (length == null) ? -1 : length);
      children.sortByName();
      return children;
    } catch (Exception e) {
      LOG.error("getChildren failed for " + ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("createChild")
  public TaxonomyNode createChild(@RequestParam(value = SITE, required = false) String siteId,
                                  @RequestParam(TAXONOMY_ID) String taxonomyId,
                                  @RequestParam(NODE_REF) String ref,
                                  @RequestParam(DEFAULT_NAME) String defaultName) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      TaxonomyNode node = (ref == null)
              ? strategy.getRoot()
              : strategy.getNodeByRef(ref);

      TaxonomyNode newChild = strategy.createChild(node, defaultName);
      if (node.isRoot()) {
        waitUntilSearchable(newChild);
      }
      return newChild;
    } catch (Exception e) {
      LOG.error("create failed for " + ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @GetMapping("commit")
  public TaxonomyNode commit(@RequestParam(value = SITE, required = false) String siteId,
                             @RequestParam(TAXONOMY_ID) String taxonomyId,
                             @RequestParam(NODE_REF) String ref) {
    try {
      Taxonomy strategy = getTaxonomy(siteId, taxonomyId);
      TaxonomyNode node = (ref == null)
              ? strategy.getRoot()
              : strategy.getNodeByRef(ref);

      return strategy.commit(node);
    } catch (Exception e) {
      LOG.error("commit failed for {}", ref, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }


  @GetMapping("suggestions")
  public TaxonomyNodeList suggestions(@RequestParam(value = SITE, required = false) String siteId,
                                      @RequestParam(TAXONOMY_ID) String taxonomyId,
                                      @RequestParam("semanticStrategyId") String semanticStrategyId,
                                      @RequestParam(ID) String id,
                                      @RequestParam(MAX) int max) {
    TaxonomyNodeList list = new TaxonomyNodeList();
    try {
      Taxonomy taxonomyStrategy = getTaxonomy(siteId, taxonomyId);
      if (semanticStrategyId != null) {
        SemanticStrategy semanticStrategy = semanticStrategyById.get(semanticStrategyId.toLowerCase()); //NOSONAR
        //the strategy may have been disabled
        if (semanticStrategy != null) {
          Suggestions suggestions = semanticStrategy.suggestions(taxonomyStrategy, id);
          List<Suggestion> result = suggestions.asList(max);
          for (Suggestion match : result) {
            String restId = TaxonomyUtil.getRestIdFromCapId(match.getId());
            TaxonomyNode hit = taxonomyStrategy.getNodeByRef(restId);
            TaxonomyNodeList nodeList = taxonomyStrategy.getPath(hit).getPath();
            hit.setPath(nodeList);
            hit.setWeight(match.getWeight());
            list.getNodes().add(hit);
          }
        }
        else {
          LOG.warn("Semantic strategy '{0}' not found, returning empty suggestion list.", semanticStrategyId);
        }
      }

    } catch (Exception e) {
      LOG.error("suggestions failed for " + semanticStrategyId + "/" + id, e);
      throw new WebApplicationException(e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return list;
  }

  // === Helper ===

  /**
   * Finds the taxonomy strategy for the given taxonomy id and site.
   */
  private Taxonomy getTaxonomy(String siteId, String taxonomyId) throws IllegalArgumentException {
    Taxonomy taxonomyStrategy = strategyResolver.getTaxonomy(siteId, taxonomyId);
    if (taxonomyStrategy == null) {
      throw new IllegalArgumentException("No taxonomy strategy found for site id '" + siteId + "' and taxonomy id '" + taxonomyId + "', " +
              "or taxonomy is not readable.");
    }
    return taxonomyStrategy;
  }

  /**
   * Returns only those strategies that are searchable during the admin view.
   *
   * @param siteId Then id of the site to filter the taxonomies or null.
   * @return The ITaxonomy instance that will be shown in the administration console.
   */
  private Collection<Taxonomy> getTaxonomiesForAdministration(String siteId) {
    List<Taxonomy> result = new ArrayList<>();
    for (Taxonomy taxonomy : strategyResolver.getTaxonomies()) {
      if (!taxonomy.isWriteable()) {
        continue;
      }

      if ((siteId == null || taxonomy.getSiteId() == null || taxonomy.getSiteId().equals(siteId)) && taxonomy.isValid()) {
        result.add(taxonomy);
      }
    }
    return result;
  }

  /**
   * Waits until the given node is searchable.
   *
   * @param node The node to wait for.
   */
  private void waitUntilSearchable(TaxonomyNode node) throws InterruptedException {
    TaxonomyNode root = getRoot(node.getSiteId(), node.getTaxonomyId());
    int attempts = 0;
    TaxonomyNodeList list = getChildren(node.getSiteId(), node.getTaxonomyId(), root.getRef(), null, null);
    while (!list.contains(node)) {
      list = getChildren(node.getSiteId(), node.getTaxonomyId(), root.getRef(), null, null);
      // These numbers are not "magic"
      Thread.sleep(500);  //NOSONAR
      attempts++;
      if (attempts == 20) {  //NOSONAR
        break;
      }
    }
  }

  // === Dependency Injection ===

  @PostConstruct
  public void afterPropertiesSet() {
    for (SemanticStrategy strategy : semanticStrategies) {
      semanticStrategyById.put(strategy.getServiceId().toLowerCase(), strategy); //NOSONAR
    }
  }
}