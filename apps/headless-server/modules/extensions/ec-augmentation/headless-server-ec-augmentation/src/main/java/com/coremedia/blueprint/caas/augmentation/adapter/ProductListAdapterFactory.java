package com.coremedia.blueprint.caas.augmentation.adapter;

import com.coremedia.blueprint.base.settings.SettingsService;
import com.coremedia.blueprint.caas.augmentation.model.AugmentationFacade;
import com.coremedia.caas.model.adapter.ExtendedLinkListAdapterFactory;
import com.coremedia.cap.content.Content;
import com.coremedia.cap.multisite.SitesService;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

@DefaultAnnotation(NonNull.class)
public class ProductListAdapterFactory {
  private final SettingsService settingsService;
  private final SitesService sitesService;
  private final ExtendedLinkListAdapterFactory extendedLinkListAdapterFactory;
  private final AugmentationFacade augmentationFacade;
  private final CommerceSearchFacade commerceSearchFacade;

  public ProductListAdapterFactory(SettingsService settingsService,
                                   SitesService sitesService,
                                   ExtendedLinkListAdapterFactory extendedLinkListAdapterFactory,
                                   AugmentationFacade augmentationFacade,
                                   CommerceSearchFacade commerceSearchFacade) {
    this.settingsService = settingsService;
    this.sitesService = sitesService;
    this.extendedLinkListAdapterFactory = extendedLinkListAdapterFactory;
    this.augmentationFacade = augmentationFacade;
    this.commerceSearchFacade = commerceSearchFacade;
  }

  public ProductListAdapter to(Content productList) {
    return to(productList, ProductListAdapter.OFFSET_DEFAULT);
  }

  public ProductListAdapter to(Content productList, Integer offset) {
    return new ProductListAdapter(extendedLinkListAdapterFactory, productList, settingsService, augmentationFacade,
            commerceSearchFacade, Objects.requireNonNull(sitesService.getContentSiteAspect(productList).getSite()), offset);
  }
}
