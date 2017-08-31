package com.coremedia.ecommerce.studio.components.link {
import com.coremedia.cms.editor.sdk.premular.fields.LinkListGridPanel;
import com.coremedia.cms.editor.sdk.util.ImageLinkListRenderer;
import com.coremedia.ecommerce.studio.helper.AugmentationUtil;
import com.coremedia.ecommerce.studio.helper.CatalogHelper;
import com.coremedia.ecommerce.studio.model.CatalogObject;
import com.coremedia.ui.data.ValueExpression;
import com.coremedia.ui.store.BeanRecord;

public class CatalogLinkPropertyFieldBase extends LinkListGridPanel {

  private var content:*;

  [Bindable]
  public var bindTo:ValueExpression;

  [Bindable]
  public var forceReadOnlyValueExpression:ValueExpression;

  public function CatalogLinkPropertyFieldBase(config:CatalogLinkPropertyField = null) {
    super(config);
    bindTo = config.bindTo;
  }


  [ProvideToExtChildren]
  internal function getContent():* {
    return content;
  }

  internal static function convertTypeLabel(v:String, catalogObject:CatalogObject):String {
    if (catalogObject is CatalogObject) {
      return AugmentationUtil.getTypeLabel(catalogObject)
    }
  }

  internal static function convertTypeCls(v:String, catalogObject:CatalogObject):String {
    if (catalogObject is CatalogObject) {
      return AugmentationUtil.getTypeCls(catalogObject)
    }
  }

  internal static function convertIdLabel(v:String, catalogObject:CatalogObject):String {
    if (!catalogObject) {
      return undefined;
    }
    var extId:String = catalogObject.getExternalId();
    if (extId) {
      return extId;
    } else if (extId == null) {
      return CatalogHelper.getInstance().getExternalIdFromId(catalogObject.getUri());
    }
    return undefined;
  }

  internal static function convertNameLabel(v:String, catalogObject:CatalogObject):String {
    var name:String = undefined;
    if (!catalogObject) return name;
    if (catalogObject is CatalogObject) {
      try {
        name = CatalogHelper.getInstance().getDecoratedName(catalogObject);
      } catch(e:Error){
        //ignore
      }
    }
    if (!name) {
      name = CatalogHelper.getInstance().getExternalIdFromId(catalogObject.getUri());
    }
    return name;
  }

  protected static function thumbColRenderer(value:Object, metaData:Object, record:BeanRecord):String {
    return ImageLinkListRenderer.thumbColRenderer(value, metaData, record, "CatalogObject");
  }
}
}
