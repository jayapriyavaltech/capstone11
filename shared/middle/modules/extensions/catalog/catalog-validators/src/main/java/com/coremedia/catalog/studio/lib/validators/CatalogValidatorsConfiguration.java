package com.coremedia.catalog.studio.lib.validators;

import com.coremedia.blueprint.base.ecommerce.content.CmsCatalogConfiguration;
import com.coremedia.blueprint.base.ecommerce.content.CmsCatalogTypes;
import com.coremedia.blueprint.base.links.UrlPathFormattingHelper;
import com.coremedia.blueprint.base.rest.validators.ChannelNavigationValidator;
import com.coremedia.blueprint.base.rest.validators.ChannelReferrerValidator;
import com.coremedia.blueprint.base.rest.validators.ChannelSegmentValidator;
import com.coremedia.blueprint.base.rest.validators.UniqueInSiteStringValidator;
import com.coremedia.cap.content.ContentRepository;
import com.coremedia.cap.multisite.SitesService;
import com.coremedia.cap.util.ContentStringPropertyIndex;
import com.coremedia.rest.validators.NotEmptyValidator;
import com.coremedia.rest.validators.RegExpValidator;
import com.coremedia.springframework.xml.ResourceAwareXmlBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportResource;

import static java.util.Arrays.asList;

@Configuration(proxyBeanMethods = false)
@Import({
        CmsCatalogConfiguration.class,
})
@ImportResource(value = {
        "classpath:/com/coremedia/blueprint/base/links/bpbase-urlpathformatting.xml",
        "classpath:/com/coremedia/cap/multisite/multisite-services.xml", // for "sitesService"
        "classpath:/framework/spring/bpbase-ec-cms-connection.xml",
        "classpath:/framework/spring/bpbase-ec-cms-commercebeans.xml",
        "classpath:/com/coremedia/blueprint/ecommerce/segments/ecommerce-segments.xml"

}, reader = ResourceAwareXmlBeanDefinitionReader.class)
public class CatalogValidatorsConfiguration {

  @Bean
  UniqueInSiteStringValidator cmsCatalogUniqueProductCodeValidator(ContentRepository contentRepository,
                                                                   CmsCatalogTypes cmsCatalogTypes,
                                                                   ContentStringPropertyIndex cmsProductCodeIndex,
                                                                   SitesService sitesService) {
    UniqueInSiteStringValidator uniqueInSiteStringValidator =
            new UniqueInSiteStringValidator(contentRepository,
                    cmsCatalogTypes.getProductContentType(),
                    cmsCatalogTypes.getProductCodeProperty(),
                    cmsProductCodeIndex.createContentsByValueFunction(),
                    sitesService);
    uniqueInSiteStringValidator.setValidatingSubtypes(true);
    return uniqueInSiteStringValidator;
  }

  @Bean
  NotEmptyValidator notEmptyValidatorProductName() {
    NotEmptyValidator validator = new NotEmptyValidator();
    validator.setProperty("productName");
    return validator;
  }

  @Bean
  NotEmptyValidator notEmptyValidatorProductCode() {
    NotEmptyValidator validator = new NotEmptyValidator();
    validator.setProperty("productCode");
    return validator;
  }

  @Bean
  RegExpValidator regExpValidatorProductCode() {
    RegExpValidator validator = new RegExpValidator();
    validator.setProperty("productCode");
    validator.setRegExp("[^:/\\s]*");
    return validator;
  }

  @Bean
  CatalogProductValidator catalogProductValidator(NotEmptyValidator notEmptyValidatorProductName,
                                                  NotEmptyValidator notEmptyValidatorProductCode,
                                                  RegExpValidator regExpValidatorProductCode) {
    CatalogProductValidator catalogProductValidator = new CatalogProductValidator();
    catalogProductValidator.setContentType("CMProduct");

    catalogProductValidator.setValidators(asList(
            notEmptyValidatorProductName,
            notEmptyValidatorProductCode,
            regExpValidatorProductCode
    ));

    return catalogProductValidator;
  }

  @Bean
  CatalogCategoryValidator catalogCategoryValidator() {
    CatalogCategoryValidator catalogCategoryValidator = new CatalogCategoryValidator();
    catalogCategoryValidator.setContentType("CMCategory");
    catalogCategoryValidator.setLiveContextSettingName("LiveContext");
    return catalogCategoryValidator;
  }

  @Bean
  ChannelSegmentValidator cmCategorySegmentValidator(UrlPathFormattingHelper urlPathFormattingHelper) {
    ChannelSegmentValidator channelSegmentValidator = new ChannelSegmentValidator(urlPathFormattingHelper);
    channelSegmentValidator.setContentType("CMCategory");
    return channelSegmentValidator;
  }

  @Bean
  ChannelNavigationValidator cmCategoryNavigationValidator() {
    ChannelNavigationValidator channelNavigationValidator = new ChannelNavigationValidator();
    channelNavigationValidator.setContentType("CMCategory");
    channelNavigationValidator.setChannelLoopCode("category_loop");
    return channelNavigationValidator;
  }

  @Bean
  ChannelReferrerValidator cmCategoryReferrerValidator() {
    ChannelReferrerValidator channelReferrerValidator = new ChannelReferrerValidator();
    channelReferrerValidator.setContentType("CMCategory");
    channelReferrerValidator.setDuplicateReferrerCode("duplicate_category_parent");
    return channelReferrerValidator;
  }
}