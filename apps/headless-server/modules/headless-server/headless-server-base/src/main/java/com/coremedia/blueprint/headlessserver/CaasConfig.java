package com.coremedia.blueprint.headlessserver;

import com.coremedia.blueprint.base.caas.model.adapter.NavigationAdapterFactory;
import com.coremedia.blueprint.base.caas.model.adapter.PageGridAdapterFactory;
import com.coremedia.blueprint.base.caas.model.adapter.QueryListAdapterFactory;
import com.coremedia.blueprint.base.caas.model.adapter.SearchServiceAdapterFactory;
import com.coremedia.blueprint.base.caas.model.adapter.SettingsAdapterFactory;
import com.coremedia.blueprint.base.navigation.context.ContextStrategy;
import com.coremedia.blueprint.base.pagegrid.ContentBackedPageGridService;
import com.coremedia.blueprint.base.settings.SettingsService;
import com.coremedia.blueprint.base.tree.TreeRelation;
import com.coremedia.blueprint.image.transformation.ImageTransformationConfiguration;
import com.coremedia.caas.filter.ValidityDateFilterPredicate;
import com.coremedia.caas.link.GraphQLLink;
import com.coremedia.caas.media.ResponsiveMediaAdapterFactory;
import com.coremedia.caas.model.ContentRoot;
import com.coremedia.caas.model.adapter.ContentBlobAdapterFactory;
import com.coremedia.caas.model.adapter.ExtendedLinkListAdapterFactory;
import com.coremedia.caas.model.adapter.LinkListAdapter;
import com.coremedia.caas.model.adapter.LinkListAdapterFactory;
import com.coremedia.caas.model.adapter.RichTextAdapter;
import com.coremedia.caas.model.adapter.RichTextAdapterFactory;
import com.coremedia.caas.model.converter.RichTextToStringConverter;
import com.coremedia.caas.model.converter.RichTextToTreeConverter;
import com.coremedia.caas.model.converter.StructToNestedMapsConverter;
import com.coremedia.caas.model.mapper.CompositeModelMapper;
import com.coremedia.caas.model.mapper.FilteringModelMapper;
import com.coremedia.caas.model.mapper.ModelMapper;
import com.coremedia.caas.model.mapper.ModelMappingPropertyAccessor;
import com.coremedia.caas.model.mapper.ModelMappingWiringFactory;
import com.coremedia.caas.richtext.RichtextTransformerReader;
import com.coremedia.caas.richtext.RichtextTransformerRegistry;
import com.coremedia.caas.richtext.config.loader.ClasspathConfigResourceLoader;
import com.coremedia.caas.richtext.config.loader.ConfigResourceLoader;
import com.coremedia.caas.richtext.stax.writer.transfer.ElementRepresentation;
import com.coremedia.caas.schema.CoercingMap;
import com.coremedia.caas.schema.CoercingRichTextTree;
import com.coremedia.caas.schema.SchemaParser;
import com.coremedia.caas.search.id.CaasContentBeanIdScheme;
import com.coremedia.caas.search.solr.SolrCaeQueryBuilder;
import com.coremedia.caas.search.solr.SolrQueryBuilder;
import com.coremedia.caas.search.solr.SolrSearchResultFactory;
import com.coremedia.caas.service.cache.CacheInstances;
import com.coremedia.caas.service.cache.Weighted;
import com.coremedia.caas.spel.SpelDirectiveWiring;
import com.coremedia.caas.spel.SpelEvaluationStrategy;
import com.coremedia.caas.spel.SpelFunctions;
import com.coremedia.caas.web.CaasServiceConfig;
import com.coremedia.caas.web.interceptor.RequestDateInitializer;
import com.coremedia.caas.web.wiring.GraphQLInvocationImpl;
import com.coremedia.caas.wiring.CapStructPropertyAccessor;
import com.coremedia.caas.wiring.CompositeTypeNameResolver;
import com.coremedia.caas.wiring.CompositeTypeNameResolverProvider;
import com.coremedia.caas.wiring.ContentRepositoryWiringFactory;
import com.coremedia.caas.wiring.ContextInstrumentation;
import com.coremedia.caas.wiring.FallbackPropertyAccessor;
import com.coremedia.caas.wiring.ProvidesTypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolver;
import com.coremedia.caas.wiring.TypeNameResolverWiringFactory;
import com.coremedia.cache.Cache;
import com.coremedia.cache.CacheCapacityConfigurer;
import com.coremedia.cap.content.Content;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.content.ContentType;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.cap.struct.Struct;
import com.coremedia.id.IdScheme;
import com.coremedia.link.CompositeLinkComposer;
import com.coremedia.link.LinkComposer;
import com.coremedia.link.uri.UriLinkBuilder;
import com.coremedia.link.uri.UriLinkComposer;
import com.coremedia.xml.Markup;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Weigher;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.Nullable;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.WiringFactory;
import graphql.spring.web.servlet.GraphQLInvocation;
import org.apache.solr.client.solrj.SolrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ConversionServiceFactoryBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Configuration
@EnableWebMvc
@ComponentScan(value = {
        "com.coremedia.caas",
        "com.coremedia.blueprint.base.caas",
        "com.coremedia.cap.undoc.common.spring",
        "com.coremedia.blueprint.caas.commerce"
})
@ImportResource({
        "classpath:/com/coremedia/blueprint/base/settings/impl/bpbase-settings-services.xml",
        "classpath:/com/coremedia/blueprint/base/multisite/bpbase-multisite-services.xml",
        "classpath:/com/coremedia/blueprint/base/pagegrid/impl/bpbase-pagegrid-services.xml",
        "classpath:/com/coremedia/blueprint/base/navigation/context/bpbase-default-contextstrategy.xml",
        "classpath:/com/coremedia/search/solr/client/coremedia-solr-client.xml",
})
@Import({ImageTransformationConfiguration.class})
public class CaasConfig implements WebMvcConfigurer {

  private static final Logger LOG = LoggerFactory.getLogger(CaasConfig.class);
  private static final String OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX = "query-root:";

  @Value("${graphiql.enabled:false}")
  private boolean isGraphiqlEnabled;

  @Value("${caas.swagger.enabled:false}")
  private boolean isSwaggerEnabled;

  private CaasServiceConfig serviceConfig;

  public CaasConfig(CaasServiceConfig serviceConfig) {
    this.serviceConfig = serviceConfig;
  }

  @Override
  public void configurePathMatch(PathMatchConfigurer matcher) {
    matcher.setUseSuffixPatternMatch(false);
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**")
            .allowedOrigins("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowCredentials(true);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new RequestDateInitializer(serviceConfig.isPreview())).addPathPatterns("/**");
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> resources = new ArrayList<>(Arrays.asList("/static/**", "/docs/**"));
    List<String> resourceLocations = new ArrayList<>();
    if (isGraphiqlEnabled) {
      resources.add("/graphiql/static/**");
      resourceLocations.add("classpath:/static/docs/");
    }
    if (isSwaggerEnabled) {
      resources.add("swagger-ui.html");
      resources.add("/webjars/**");
      resourceLocations.add("classpath:/META-INF/resources/webjars/");
    }
    if (serviceConfig.isPreview()) {
      resourceLocations.add("classpath:/static/");
      resourceLocations.add("classpath:/META-INF/resources/");
    }
    registry.addResourceHandler(resources.toArray(new String[0]))
            .addResourceLocations(resourceLocations.toArray(new String[0]));
  }

  @Bean
  @ConditionalOnProperty("caas.logRequests")
  public Filter logFilter() {
    CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter() {
      @Override
      protected boolean shouldLog(HttpServletRequest request) {
        return true;
      }

      @Override
      protected void beforeRequest(HttpServletRequest request, @Nullable String message) {
        if (!RequestMethod.OPTIONS.name().equals(request.getMethod())) {
          LOG.trace(message);
        }
      }
    };
    filter.setIncludeQueryString(true);
    filter.setIncludePayload(false);
    return filter;
  }

  @Bean("cacheManager")
  @SuppressWarnings("unchecked")
  public CacheManager cacheManager() {
    ImmutableList.Builder<org.springframework.cache.Cache> builder = ImmutableList.builder();
    // richtext cache
    if (serviceConfig.getCacheSpecs().containsKey(CacheInstances.RICHTEXT)) {
      com.github.benmanes.caffeine.cache.Cache cache = Caffeine.from(serviceConfig.getCacheSpecs().get(CacheInstances.RICHTEXT))
              .weigher((Weigher<Object, Weighted>) (key, value) -> value.getWeight())
              .build();
      builder.add(new CaffeineCache(CacheInstances.RICHTEXT, cache));
    }
    SimpleCacheManager cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(builder.build());
    return cacheManager;
  }

  @Bean(name = "cacheCapacityConfigurer", initMethod = "init")
  public CacheCapacityConfigurer configureCache(Cache cache) {
    CacheCapacityConfigurer configurer = new CacheCapacityConfigurer();
    configurer.setCache(cache);
    configurer.setCapacities(serviceConfig.getCacheCapacities());
    return configurer;
  }

  @Bean
  public LinkComposer<Object, GraphQLLink> graphQlLinkComposer(List<LinkComposer<?, ? extends GraphQLLink>> linkComposers) {
    return new CompositeLinkComposer<>(linkComposers, emptyList());
  }

  @Bean
  public LinkComposer<Object, String> uriLinkComposer(List<LinkComposer<?, ? extends UriLinkBuilder>> linkComposers) {
    return new UriLinkComposer<>(
            new CompositeLinkComposer<>(linkComposers, emptyList()));
  }

  /**
   * Returns a type resolver mapping content type names to GraphQL object types by appending the string "Impl".
   * If such a GraphQL type exists within the schema, it is returned (wrapped with an Optional).
   * Otherwise, the name of the parent content type is tried (again with the string "Impl" appended),
   * and so on until either a corresponding GraphQL type is found or the top of the content type hierarchy is reached.
   * In the latter case, an empty Optional is returned.
   */
  @Bean
  public TypeNameResolver<Content> contentTypeNameResolver(@Lazy GraphQLSchema schema) {
    return content -> {
      ContentType currentContentType = content.getType();
      while (currentContentType != null) {
        String typeName = currentContentType.getName() + "Impl";
        GraphQLObjectType type = schema.getObjectType(typeName);
        if (type != null) {
          return Optional.of(type.getName());
        }
        currentContentType = currentContentType.getParent();
      }
      return Optional.empty();
    };
  }

  @Bean
  public ProvidesTypeNameResolver providesContentTypeNameResolver(ContentRepository repository) {
    return typeName ->
      "Banner".equals(typeName) ||
      "Detail".equals(typeName) ||
      "CollectionItem".equals(typeName) ||
      repository.getContentType(typeName) != null ? Optional.of(true) : Optional.empty();
  }

  @Bean
  public TypeNameResolver<Object> compositeTypeNameResolver(List<TypeNameResolver<?>> typeNameResolvers) {
    return new CompositeTypeNameResolver<>(typeNameResolvers);
  }

  @Bean
  public ProvidesTypeNameResolver compositeProvidesTypeNameResolver(List<ProvidesTypeNameResolver> providesTypeNameResolvers) {
    return new CompositeTypeNameResolverProvider(providesTypeNameResolvers);
  }

  @Bean
  public TypeNameResolverWiringFactory typeNameResolverWiringFactory(
          @Qualifier("compositeProvidesTypeNameResolver") ProvidesTypeNameResolver providesTypeNameResolver,
          @Qualifier("compositeTypeNameResolver") TypeNameResolver<Object> typeNameResolver) {
    return new TypeNameResolverWiringFactory(providesTypeNameResolver, typeNameResolver);
  }

  @Bean
  public NavigationAdapterFactory navigationAdapter(@Qualifier("contentContextStrategy") ContextStrategy<Content, Content> contextStrategy, Map<String, TreeRelation<Content>> treeRelations) {
    return new NavigationAdapterFactory(contextStrategy, treeRelations);
  }

  @Bean
  public LinkListAdapterFactory mediaLinkListAdapter() {
    return new LinkListAdapterFactory("pictures");
  }

  @Bean
  public LinkListAdapterFactory teaserMediaLinkListAdapter(@Qualifier("teaserTargetsAdapter") ExtendedLinkListAdapterFactory teaserTargetsAdapter) {
    return new LinkListAdapterFactory("pictures",
            content -> teaserTargetsAdapter.to(content).getTargets().stream()
                    .flatMap(target -> target.getLinks("pictures").stream()
                    ));
  }

  @Bean
  public LinkListAdapterFactory channelMediaLinkListAdapter(@Qualifier("pageGridAdapter") PageGridAdapterFactory pageGridAdapter,
                                                            @Qualifier("mediaLinkListAdapter") LinkListAdapterFactory mediaLinkListAdapter,
                                                            @Qualifier("teaserMediaLinkListAdapter") LinkListAdapterFactory teaserMediaLinkListAdapter) {
    return new LinkListAdapterFactory("pictures",
            content -> pageGridAdapter.to(content, "placement").getRows().stream()
                    .flatMap(target -> target.getPlacements().stream())
                    .flatMap(placement -> placement.getItems().stream())
                    .map(teasable -> teasable.getType().isSubtypeOf("CMTeaser")
                            ? teaserMediaLinkListAdapter.to(teasable, "CMMedia")
                            : mediaLinkListAdapter.to(teasable, "CMMedia"))
                    .map(LinkListAdapter::first)
                    .filter(Objects::nonNull));
  }

  @Bean
  public ExtendedLinkListAdapterFactory teaserTargetsAdapter() {
    return new ExtendedLinkListAdapterFactory("targets", "links", "target", "CMLinkable", "target");
  }

  @Bean
  public ExtendedLinkListAdapterFactory collectionExtendedItemsAdapter() {
    return new ExtendedLinkListAdapterFactory("extendedItems", "links", "items", "CMLinkable", "target");
  }

  @Bean
  public PageGridAdapterFactory pageGridAdapter(@Qualifier("contentBackedPageGridService") ContentBackedPageGridService contentBackedPageGridService) {
    return new PageGridAdapterFactory(contentBackedPageGridService);
  }

  @Bean
  public SettingsAdapterFactory settingsAdapter(@Qualifier("settingsService") SettingsService settingsService) {
    return new SettingsAdapterFactory(settingsService);
  }

  @Bean
  public ContentBlobAdapterFactory contentBlobAdapter() {
    return new ContentBlobAdapterFactory();
  }

  @Bean
  public SolrSearchResultFactory searchResultFactory(@Qualifier("solrClient") SolrClient solrClient,
                                                     ContentRepository contentRepository,
                                                     @Value("${caas.solr.collection}") String solrCollectionName,
                                                     @Value("${caas.querylist.search.cache.seconds}") Integer cacheForSeconds) {
    SolrSearchResultFactory solrSearchResultFactory = new SolrSearchResultFactory(contentRepository, solrClient, solrCollectionName);
    if (!serviceConfig.isPreview()) {
      solrSearchResultFactory.setCacheForSeconds(cacheForSeconds);
    }
    return solrSearchResultFactory;
  }

  @Bean
  public SolrSearchResultFactory queryListSearchResultFactory(@Qualifier("solrClient") SolrClient solrClient,
                                                              ContentRepository contentRepository,
                                                              @Value("${caas.solr.collection}") String solrCollectionName,
                                                              @Value("${caas.search.cache.seconds}") Integer cacheForSeconds) {
    SolrSearchResultFactory solrSearchResultFactory = new SolrSearchResultFactory(contentRepository, solrClient, solrCollectionName);
    if (!serviceConfig.isPreview()) {
      solrSearchResultFactory.setCacheForSeconds(cacheForSeconds);
    }
    return solrSearchResultFactory;
  }

  @Bean
  public SearchServiceAdapterFactory searchServiceAdapter(@Qualifier("searchResultFactory") SolrSearchResultFactory searchResultFactory,
                                                          ContentRepository contentRepository,
                                                          @Qualifier("settingsService") SettingsService settingsService,
                                                          SitesService sitesService,
                                                          List<IdScheme> idSchemes,
                                                          @Qualifier("caeSolrQueryBuilder") SolrQueryBuilder solrQueryBuilder) {
    return new SearchServiceAdapterFactory(searchResultFactory, contentRepository, settingsService, sitesService, idSchemes, solrQueryBuilder);
  }

  @Bean
  public QueryListAdapterFactory queryListAdapter(@Qualifier("queryListSearchResultFactory") SolrSearchResultFactory searchResultFactory,
                                                  ContentRepository contentRepository,
                                                  @Qualifier("settingsService") SettingsService settingsService,
                                                  SitesService sitesService,
                                                  List<IdScheme> idSchemes,
                                                  @Qualifier("dynamicContentSolrQueryBuilder") SolrQueryBuilder solrQueryBuilder,
                                                  @Qualifier("collectionExtendedItemsAdapter") ExtendedLinkListAdapterFactory collectionExtendedItemsAdapter,
                                                  @Qualifier("navigationAdapter") NavigationAdapterFactory navigationAdapterFactory) {
    return new QueryListAdapterFactory(searchResultFactory, contentRepository, settingsService, sitesService, idSchemes, solrQueryBuilder, collectionExtendedItemsAdapter, navigationAdapterFactory);
  }

  @Bean
  public IdScheme caasContentBeanIdScheme(ContentRepository contentRepository) {
    return new CaasContentBeanIdScheme(contentRepository);
  }

  @Bean
  public List<IdScheme> idSchemes(IdScheme caasContentBeanIdScheme) {
    return Collections.singletonList(caasContentBeanIdScheme);
  }

  @Bean
  public SolrQueryBuilder caeSolrQueryBuilder() {
    return new SolrCaeQueryBuilder("/cmdismax");
  }

  @Bean
  public SolrQueryBuilder dynamicContentSolrQueryBuilder() {
    return new SolrCaeQueryBuilder("/select");
  }

  @Bean
  public ModelMapper<Markup, RichTextAdapter> richTextModelMapper(ContentRepository contentRepository, ResponsiveMediaAdapterFactory mediaResource, RichtextTransformerRegistry richtextTransformerRegistry, LinkComposer<Object, String> uriLinkComposer, LinkComposer<Object, GraphQLLink> graphqlLinkComposer) {
    RichTextAdapterFactory richTextAdapterFactory = new RichTextAdapterFactory(contentRepository, mediaResource, richtextTransformerRegistry, uriLinkComposer, graphqlLinkComposer);
    return richTextAdapterFactory::to;
  }

  @Bean
  public ModelMapper<GregorianCalendar, ZonedDateTime> dateModelMapper() {
    return gregorianCalendar -> Optional.of(gregorianCalendar.toZonedDateTime());
  }


  @Bean
  public ConfigResourceLoader richTextConfigResourceLoader() {
    return new ClasspathConfigResourceLoader("/");
  }

  @Bean
  public RichtextTransformerRegistry richtextTransformerRegistry(@Qualifier("richTextConfigResourceLoader") ConfigResourceLoader resourceLoader, @Qualifier("cacheManager") CacheManager cacheManager) throws IOException {
    return new RichtextTransformerReader(resourceLoader, cacheManager).read();
  }

  @Bean
  public PropertyAccessor mapPropertyAccessor() {
    return new MapAccessor();
  }

  @Bean
  public PropertyAccessor reflectivePropertyAccessor() {
    return new ReflectivePropertyAccessor();
  }

  @Bean
  public PropertyAccessor capStructPropertyAccessor() {
    return new CapStructPropertyAccessor();
  }

  @Bean
  @Qualifier("propertyAccessors")
  public List<PropertyAccessor> propertyAccessors(List<PropertyAccessor> propertyAccessors, ModelMapper<Object, Object> modelMapper) {
    return Stream.concat(propertyAccessors.stream()
                    .map(propertyAccessor -> new ModelMappingPropertyAccessor(propertyAccessor, modelMapper)),
            Stream.of(new FallbackPropertyAccessor()))
            .collect(Collectors.toList());
  }

  @Bean
  public SpelEvaluationStrategy spelEvaluationStrategy(BeanFactory beanFactory, @Qualifier("propertyAccessors") List<PropertyAccessor> propertyAccessors) {
    return new SpelEvaluationStrategy(beanFactory, propertyAccessors);
  }

  @Bean
  @Qualifier("globalSpelVariables")
  public Method first() throws NoSuchMethodException {
    return SpelFunctions.class.getDeclaredMethod("first", List.class);
  }

  @Bean
  public SchemaDirectiveWiring fetch(SpelEvaluationStrategy spelEvaluationStrategy,
                                     @Qualifier("graphQlConversionService") ConversionService conversionService,
                                     @Qualifier("globalSpelVariables") Map<String, Object> globalSpelVariables,
                                     @Qualifier("conversionTypeMap") Map<String, Class<?>> conversionTypeMap) {
    return new SpelDirectiveWiring(spelEvaluationStrategy, conversionService, globalSpelVariables, conversionTypeMap);
  }

  @Bean
  public RichTextToStringConverter richTextToStringConverter() {
    return new RichTextToStringConverter();
  }

  @Bean
  public RichTextToTreeConverter richTextToTreeConverter() {
    return new RichTextToTreeConverter();
  }

  @Bean
  public Converter<Struct, Map> structToNestedMapsConverter() {
    return new StructToNestedMapsConverter();
  }

  @Bean
  public ConversionServiceFactoryBean graphQlConversionService(Set<Converter> converters) {
    ConversionServiceFactoryBean conversionServiceFactoryBean = new ConversionServiceFactoryBean();
    conversionServiceFactoryBean.setConverters(converters);
    return conversionServiceFactoryBean;
  }

  @Bean
  public ContentRepositoryWiringFactory contentRepositoryWiringFactory(Map<String, GraphQLScalarType> builtinScalars,
                                                                       ContentRepository repository,
                                                                       @Qualifier("graphQlConversionService") ConversionService conversionService,
                                                                       @Qualifier("conversionTypeMap") Map<String, Class<?>> conversionTypeMap) {
    return new ContentRepositoryWiringFactory(repository, builtinScalars, conversionService, conversionTypeMap);
  }

  @Bean
  @Qualifier("filterPredicate")
  public Predicate<Object> validityDateFilterPredicate() {
    return new ValidityDateFilterPredicate();
  }

  @Bean
  public ModelMapper<Object, Object> rootModelMapper(List<ModelMapper<?, ?>> modelMappers, @Qualifier("filterPredicate") List<Predicate<Object>> predicates) {
    return new FilteringModelMapper(new CompositeModelMapper<>(modelMappers), predicates);
  }

  @Bean
  @Qualifier("queryRoot")
  public ContentRoot content(ContentRepository repository, SitesService sitesService) {
    return new ContentRoot(repository, sitesService);
  }

  @Bean
  public GraphQLInvocation graphQLInvocation(GraphQL graphQL, @Qualifier("queryRoot") Map<String, Object> queryRoots) {
    return new GraphQLInvocationImpl(graphQL, renameQueryRootsWithOptionalPrefix(queryRoots));
  }

  @Bean
  public ContextInstrumentation contextInstrumentation() {
    return new ContextInstrumentation();
  }

  @Bean
  public TypeDefinitionRegistry typeDefinitionRegistry()
          throws IOException {
    SchemaParser schemaParser = new SchemaParser();
    PathMatchingResourcePatternResolver loader = new PathMatchingResourcePatternResolver();
    var typeRegistry = new TypeDefinitionRegistry();
    for (var resource : loader.getResources("classpath*:*-schema.graphql")) {
      LOG.info("merging GraphQL schema {}", resource.getURI());
      InputStreamReader in = new InputStreamReader(resource.getInputStream());
      TypeDefinitionRegistry newRegistry = schemaParser.parse(in);
      typeRegistry.merge(newRegistry);
    }
    return typeRegistry;
  }

  @Bean
  public GraphQLSchema graphQLSchema(Map<String, SchemaDirectiveWiring> directiveWirings,
                                     @Qualifier("rootModelMapper") ModelMapper<Object, Object> modelMapper,
                                     List<WiringFactory> wiringFactories)
          throws IOException {
    WiringFactory wiringFactory = new ModelMappingWiringFactory(modelMapper, wiringFactories);
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring()
            .wiringFactory(wiringFactory);
    directiveWirings.forEach(builder::directive);
    RuntimeWiring wiring = builder.build();
    TypeDefinitionRegistry typeRegistry = typeDefinitionRegistry();
    SchemaGenerator schemaGenerator = new SchemaGenerator();
    return schemaGenerator.makeExecutableSchema(typeRegistry, wiring);
  }

  @Bean
  public GraphQL graphQL(GraphQLSchema graphQLSchema,
                         List<Instrumentation> instrumentations) {
    return GraphQL.newGraphQL(graphQLSchema)
            .instrumentation(new ChainedInstrumentation(instrumentations))
            .build();
  }


  @Bean
  @Qualifier("conversionTypeMap")
  public Map<String, Class<?>> conversionTypeMap() {

    /* add corresponding custom scalar types from content-schema.graphql here */
    return new ImmutableMap.Builder<String, Class<?>>()
            .put("MapOfString", Map.class)
            .put("MapOfInt", Map.class)
            .put("MapOfLong", Map.class)
            .put("MapOfFloat", Map.class)
            .put("MapOfBoolean", Map.class)
            .put("RichTextTree", ElementRepresentation.class)
            .build();

  }

  @Bean
  public GraphQLScalarType MapOfString(ConversionService conversionService) {
    return new GraphQLScalarType("MapOfString", "Built-in map of scalar type", new CoercingMap<>(String.class, conversionService));
  }

  @Bean
  public GraphQLScalarType MapOfInt(ConversionService conversionService) {
    return new GraphQLScalarType("MapOfInt", "Map of Integer", new CoercingMap<>(Integer.class, conversionService));
  }

  @Bean
  public GraphQLScalarType MapOfLong(ConversionService conversionService) {
    return new GraphQLScalarType("MapOfLong", "Map of Long", new CoercingMap<>(Long.class, conversionService));
  }

  @Bean
  public GraphQLScalarType MapOfFloat(ConversionService conversionService) {
    return new GraphQLScalarType("MapOfFloat", "Map of Float", new CoercingMap<>(Double.class, conversionService));
  }

  @Bean
  public GraphQLScalarType MapOfBoolean(ConversionService conversionService) {
    return new GraphQLScalarType("MapOfBoolean", "Map of Boolean", new CoercingMap<>(Boolean.class, conversionService));
  }

  @Bean
  public GraphQLScalarType RichTextTree() {
    return new GraphQLScalarType("RichTextTree", "Built-in rich text object tree", new CoercingRichTextTree());
  }

  private Map<String, Object> renameQueryRootsWithOptionalPrefix(Map<String, Object> queryRoots) {
    Map<String, Object> renamedQueryRoots = new LinkedHashMap<>(queryRoots.size());
    for (var rootEntry : queryRoots.entrySet()) {
      String name = rootEntry.getKey();
      if (name.startsWith(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX)) {
        name = name.substring(OPTIONAL_QUERY_ROOT_BEAN_NAME_PREFIX.length());
        LOG.info("adding GraphQL query root {} (renamed from {})", name, rootEntry.getKey());
      } else {
        LOG.info("adding GraphQL query root {}", name);
      }
      renamedQueryRoots.put(name, rootEntry.getValue());
    }
    return renamedQueryRoots;
  }

}