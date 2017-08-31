package com.coremedia.ecommerce.studio.rest;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.DefaultConnection;
import com.coremedia.livecontext.ecommerce.workspace.Workspace;
import com.coremedia.livecontext.ecommerce.workspace.WorkspaceService;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * A catalog {@link com.coremedia.livecontext.ecommerce.p13n.Segment} object as a RESTful resource.
 */
@Produces(MediaType.APPLICATION_JSON)
@Path("livecontext/workspace/{siteId:[^/]+}/{id:[^/]+}")
public class WorkspaceResource extends AbstractCatalogResource<Workspace> {

  @Override
  public WorkspaceRepresentation getRepresentation() {
    WorkspaceRepresentation representation = new WorkspaceRepresentation();
    fillRepresentation(representation);
    return representation;
  }

  private void fillRepresentation(WorkspaceRepresentation representation) {
    Workspace entity = getEntity();

    if (entity == null) {
      throw new CatalogBeanNotFoundRestException("Could not load workspace bean.");
    }

    representation.setId(entity.getId());
    representation.setName(entity.getName());
    representation.setExternalId(entity.getExternalId());
    representation.setExternalTechId(entity.getExternalTechId());
  }

  @Override
  protected Workspace doGetEntity() {
    return getWorkspaceService().findWorkspaceByExternalTechId(getId());
  }

  @Override
  public void setEntity(Workspace workspace) {
    if (workspace.getId() != null) {
      String extId = getExternalIdFromId(workspace.getId());
      setId(extId);
    } else {
      setId(workspace.getExternalId());
    }
    setSiteId(workspace.getContext().getSiteId());
  }

  public WorkspaceService getWorkspaceService() {
    return DefaultConnection.get().getWorkspaceService();
  }
}
