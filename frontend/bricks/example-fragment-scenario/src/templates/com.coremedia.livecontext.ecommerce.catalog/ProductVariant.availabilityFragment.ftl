<#-- @ftlvariable name="self" type="com.coremedia.livecontext.ecommerce.catalog.ProductVariant" -->

<#-- @deprecated since 2007.1 -->
<#if (self.availabilityInfo.quantity > 0) >
  ${self.availabilityInfo.quantity}
<#else>
  N/A
</#if>
