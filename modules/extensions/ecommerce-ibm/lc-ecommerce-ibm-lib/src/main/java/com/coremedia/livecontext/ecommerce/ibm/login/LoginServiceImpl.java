package com.coremedia.livecontext.ecommerce.ibm.login;

import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommerceCache;
import com.coremedia.blueprint.base.livecontext.ecommerce.common.CommercePropertyHelper;
import com.coremedia.livecontext.ecommerce.common.CommerceException;
import com.coremedia.livecontext.ecommerce.common.CommerceRemoteException;
import com.coremedia.livecontext.ecommerce.common.StoreContext;
import com.coremedia.livecontext.ecommerce.common.UnauthorizedException;
import com.coremedia.livecontext.ecommerce.ibm.common.StoreContextHelper;
import com.coremedia.livecontext.ecommerce.workspace.WorkspaceId;
import com.coremedia.security.encryption.util.EncryptionServiceUtil;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static com.coremedia.blueprint.base.livecontext.ecommerce.common.StoreContextImpl.WORKSPACE_ID_NONE;

/**
 * Service interface to logon to the IBM WCS catalog.
 */
public class LoginServiceImpl implements LoginService, InitializingBean, DisposableBean {

  private static final Logger LOG = LoggerFactory.getLogger(LoginServiceImpl.class);

  private static final String REQUEST_ATTRIB_PREVIEW_TOKEN = LoginServiceImpl.class.getName() + "#previewToken";
  private static final String ERROR_CODE_NOT_AUTHORIZED = "2110";

  private String serviceUser;
  private String servicePassword;
  private CommerceCache commerceCache;

  private long previewTokenLifeTimeInSeconds = TimeUnit.HOURS.toSeconds(3);

  private WcLoginWrapperService loginWrapperService;

  private Map<String, WcCredentials> credentialsByStore = Collections.synchronizedMap(new HashMap<String, WcCredentials>());

  @Required
  public void setServiceUser(String serviceUser) {
    this.serviceUser = serviceUser;
  }

  @Required
  public void setServicePassword(String servicePassword) {
    this.servicePassword = EncryptionServiceUtil.decodeEntryTransparently(servicePassword);
  }

  @Required
  public void setLoginWrapperService(WcLoginWrapperService loginWrapperService) {
    this.loginWrapperService = loginWrapperService;
  }

  /**
   * sets the lifetime for generated preview tokens in seconds.
   * Value might be overriden by {@link PreviewTokenCacheKey#CONFIG_KEY_PREVIEW_TOKEN} cache duration.
   * {@link #afterPropertiesSet} ensures that {@link #previewTokenLifeTimeInSeconds} is at least twice as long as
   * {@link PreviewTokenCacheKey#CONFIG_KEY_PREVIEW_TOKEN} cache setting in {@link CommerceCache#setCacheTimesInSeconds(Map)}
   * to avoid outdated previewToken in cache.
   *
   * @param previewTokenLifeTimeInSeconds (default is 3 hours, might be increased by higher cache duration times)
   */
  public void setPreviewTokenLifeTimeInSeconds(long previewTokenLifeTimeInSeconds) {
    this.previewTokenLifeTimeInSeconds = previewTokenLifeTimeInSeconds;
  }

  @Required
  public void setCommerceCache(CommerceCache commerceCache) {
    this.commerceCache = commerceCache;
  }

  public String getServiceUser() {
    return serviceUser;
  }

  public String getServicePassword() {
    return servicePassword;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    checkRequiredPropertyNotNull("loginWrapperService", loginWrapperService);
    checkRequiredPropertyNotNull("serviceUser", serviceUser);
    checkRequiredPropertyNotNull("servicePassword", servicePassword);
    checkRequiredPropertyNotNull("commerceCache", commerceCache);

    long cacheDurationInSeconds = commerceCache.getCacheDurationInSeconds(PreviewTokenCacheKey.CONFIG_KEY_PREVIEW_TOKEN);
    //uapi cache key shall always expire before commerce previewToken expires
    if (previewTokenLifeTimeInSeconds < (cacheDurationInSeconds * 2)) {
      previewTokenLifeTimeInSeconds = cacheDurationInSeconds * 2;
      LOG.info("Increasing previewTokenLifeTimeInSeconds to (cacheDurationInSeconds * 2) = {}",
              previewTokenLifeTimeInSeconds);
    }
  }

  private static void checkRequiredPropertyNotNull(@NonNull String propertyName, Object propertyValue) {
    Objects.requireNonNull(propertyValue, "Required property not set: " + propertyName);
  }

  @Override
  public synchronized void destroy() throws Exception {
    for (Map.Entry<String, WcCredentials> sessionEntry : credentialsByStore.entrySet()) {
      String storeId = sessionEntry.getKey();
      logout(storeId);
    }
    credentialsByStore.clear();
  }

  @Nullable
  @Override
  public WcCredentials loginIdentity(String username, String password, @NonNull StoreContext context) {
    try {
      WcSession session = loginWrapperService.login(username, password, context).orElse(null);
      if (session != null) {
        return new SimpleCommerceCredentials(StoreContextHelper.getStoreId(context), session);
      }
    } catch (CommerceRemoteException e) {
      // with fep7 a CommerceRemoteException occurs when the user cannot be authorized
      // to have it uniformly we map it to an UnauthorizedException
      if (ERROR_CODE_NOT_AUTHORIZED.equals(e.getErrorCode())) {
        throw new UnauthorizedException(e.getMessage(), e.getResultCode());
      }
      throw e;
    }
    return null;
  }

  @Nullable
  @Override
  public synchronized WcCredentials loginServiceIdentity(@NonNull StoreContext context) {
    String storeId = StoreContextHelper.getStoreId(context);
    WcCredentials result = credentialsByStore.get(storeId);
    if (result == null) {
      result = loginIdentity(
              CommercePropertyHelper.replaceTokens(serviceUser, context),
              CommercePropertyHelper.replaceTokens(servicePassword, context), context);
      credentialsByStore.put(storeId, result);
    }
    return result;
  }

  @Nullable
  @Override
  public WcPreviewToken getPreviewToken(@NonNull StoreContext context) {
    WcPreviewToken result = null;

    HttpServletRequest request = getRequest();
    if (request != null) {
      result = (WcPreviewToken) request.getAttribute(REQUEST_ATTRIB_PREVIEW_TOKEN);
    }

    if (result != null) {
      return result;
    }

    try {
      String workspaceIdStr = context.getWorkspaceId()
              .filter(workspaceId -> !workspaceId.equals(WORKSPACE_ID_NONE))
              .map(WorkspaceId::value)
              .orElse(null);
      Optional<ZonedDateTime> cmPreviewDate = context.getPreviewDate();
      String ibmFormattedPreviewDate = cmPreviewDate
              .map(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")::format)
              .orElse(null);
      String timezone = cmPreviewDate
              .map(date -> TimeZone.getTimeZone(date.getZone()).getID())
              .orElse(null);
      boolean isTimeFixed = cmPreviewDate.isPresent();

      WcPreviewTokenParam previewTokenParam = new WcPreviewTokenParam(
              workspaceIdStr,
              ibmFormattedPreviewDate,
              timezone,
              isTimeFixed ? "true" : "false",
              context.getUserSegments().orElse(null),
              String.valueOf(Math.max(1, previewTokenLifeTimeInSeconds / 60))
      );

      result = commerceCache.get(
              new PreviewTokenCacheKey(previewTokenParam, context, loginWrapperService, commerceCache));

      if (request != null) {
        request.setAttribute(REQUEST_ATTRIB_PREVIEW_TOKEN, result);
      }
    } catch (CommerceException e) {
      LOG.warn("Error getting preview token for store context: {}, message: {}", context, e.getMessage());
    }

    return result;
  }

  @Override
  public boolean logoutServiceIdentity(@NonNull StoreContext context) {
    return invalidateCredentialsForStore(context);
  }

  @Override
  public WcCredentials renewServiceIdentityLogin(@NonNull StoreContext context) {
    logoutServiceIdentity(context);
    StoreContextHelper.setCredentials(context, null);
    return loginServiceIdentity(context);
  }

  @Override
  public synchronized void clearIdentityCache() {
    credentialsByStore.clear();
  }

  private synchronized boolean invalidateCredentialsForStore(@NonNull StoreContext storeContext) {
    String storeId = StoreContextHelper.getStoreId(storeContext);

    WcCredentials credentials = credentialsByStore.remove(storeId);
    return credentials != null && logout(storeId);
  }

  private boolean logout(String storeId) {
    try {
      return loginWrapperService.logout(storeId);
    } catch (Exception e) {
      StoreContext storeContext = StoreContextHelper.findCurrentContext().orElse(null);

      // NOSONAR
      LOG.warn("Ignoring error while closing REST session for user '{}', store {} ({})",
              CommercePropertyHelper.replaceTokens(serviceUser, storeContext),
              storeId, e.getMessage());
    }
    return false;
  }

  @Nullable
  private static HttpServletRequest getRequest() {
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes instanceof ServletRequestAttributes) {
      return ((ServletRequestAttributes) requestAttributes).getRequest();
    }
    return null;
  }

  @NonNull
  private static Calendar createCalendar(@NonNull Date date, @NonNull TimeZone timeZone) {
    Calendar calendar = Calendar.getInstance();

    calendar.setTime(date);
    calendar.setTimeZone(timeZone);

    return calendar;
  }
}
