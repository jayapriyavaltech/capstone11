<#-- @ftlvariable name="self" type="com.coremedia.blueprint.common.contentbeans.CMTeasable" -->

<@cm.include self=self view="defaultTeaser" params={
  "index": cm.localParameters().index!0
}/>