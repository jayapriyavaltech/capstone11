package com.coremedia.livecontext.fragment.links;

import com.coremedia.blueprint.common.contentbeans.CMLinkable;
import com.coremedia.livecontext.contentbeans.CMExternalPage;
import com.coremedia.livecontext.ecommerce.catalog.Category;
import com.coremedia.livecontext.ecommerce.catalog.Product;
import com.coremedia.livecontext.ecommerce.common.CommerceConnection;
import com.coremedia.livecontext.ecommerce.common.StoreContext;
import com.coremedia.livecontext.ecommerce.link.StorefrontRef;
import com.coremedia.livecontext.ecommerce.link.StorefrontRefKey;
import com.coremedia.livecontext.fragment.links.transformers.resolvers.seo.ExternalSeoSegmentBuilder;
import com.coremedia.livecontext.navigation.LiveContextCategoryNavigation;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

import static com.coremedia.livecontext.fragment.links.CommerceLinkTemplateTypes.CM_CONTENT;
import static com.coremedia.livecontext.fragment.links.CommerceLinkTemplateTypes.EXTERNAL_PAGE_NON_SEO;
import static com.coremedia.livecontext.fragment.links.CommerceLinkTemplateTypes.EXTERNAL_PAGE_SEO;
import static com.google.common.base.Strings.isNullOrEmpty;

@DefaultAnnotation(NonNull.class)
class CommerceContentLedLinks {

  private final CommerceLinkHelper commerceLinkHelper;
  private final ExternalSeoSegmentBuilder seoSegmentBuilder;

  CommerceContentLedLinks(CommerceLinkHelper commerceLinkHelper, ExternalSeoSegmentBuilder seoSegmentBuilder) {
    this.commerceLinkHelper = commerceLinkHelper;
    this.seoSegmentBuilder = seoSegmentBuilder;
  }

  Optional<UriComponents> buildLinkForCMLinkable(CMLinkable bean) {
    return commerceLinkHelper.findCommerceConnection(bean)
            .flatMap(commerceConnection -> buildLinkForCMLinkable(commerceConnection, bean));
  }

  private Optional<UriComponents> buildLinkForCMLinkable(CommerceConnection connection, CMLinkable cmLinkable) {
    return cmLinkable.getContexts().stream()
            .findFirst()
            .map(cmContext -> seoSegmentBuilder.asSeoSegment(cmContext, cmLinkable))
            .filter(segment -> !segment.isBlank())
            .flatMap(segment -> buildLink(connection, CM_CONTENT, Map.of("seoSegment", segment)));
  }

  Optional<UriComponents> buildLinkForCategory(Category category) {
    return Optional.ofNullable(category.getStorefrontUrl())
            .filter(StringUtils::hasText)
            .map(UriComponentsBuilder::fromUriString)
            .map(UriComponentsBuilder::build);
  }

  Optional<UriComponents> buildLinkForLiveContextCategoryNavigation(LiveContextCategoryNavigation categoryNavigation) {
    Category category = categoryNavigation.getCategory();
    return buildLinkForCategory(category);
  }

  Optional<UriComponents> buildLinkForProduct(Product product) {
    return Optional.ofNullable(product.getStorefrontUrl())
            .filter(StringUtils::hasText)
            .map(UriComponentsBuilder::fromUriString)
            .map(UriComponentsBuilder::build);
  }

  Optional<UriComponents> buildLinkForExternalPage(CMExternalPage externalPage) {
    return commerceLinkHelper.findCommerceConnection(externalPage)
            .flatMap(commerceConnection -> buildContentLedLinkForExternalPage(externalPage, commerceConnection));
  }

  private static Optional<UriComponents> buildContentLedLinkForExternalPage(CMExternalPage externalPage,
                                                           CommerceConnection commerceConnection) {

    String externalUriPath = externalPage.getExternalUriPath();

    if (!isNullOrEmpty(externalUriPath)) {
      return buildLink(commerceConnection, EXTERNAL_PAGE_NON_SEO, Map.of("uriPath", externalUriPath));
    }

    String segment = externalPage.getSegment();
    return buildLink(commerceConnection, EXTERNAL_PAGE_SEO, Map.of("seoSegment", segment));
  }

  private static Optional<UriComponents> buildLink(CommerceConnection commerceConnection,
                                                   StorefrontRefKey templateKey,
                                                   Map<String, String> replacements) {
    StoreContext storeContext = commerceConnection.getStoreContext();
    return commerceConnection.getLinkService()
            .flatMap(linkService -> linkService.getStorefrontRef(templateKey, storeContext))
            .map(storefrontRef -> storefrontRef.replace(replacements))
            .map(StorefrontRef::toLink)
            .map(UriComponentsBuilder::fromUriString)
            .map(UriComponentsBuilder::build);
  }

}
