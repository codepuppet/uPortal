/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jasig.portal.portlet.url;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.portlet.PortletMode;
import javax.portlet.WindowState;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pluto.container.PortletURLProvider;
import org.apache.pluto.container.PortletURLProvider.TYPE;
import org.jasig.portal.IUserPreferencesManager;
import org.jasig.portal.layout.IUserLayoutManager;
import org.jasig.portal.layout.node.IUserLayoutChannelDescription;
import org.jasig.portal.portlet.om.IPortletDefinition;
import org.jasig.portal.portlet.om.IPortletEntity;
import org.jasig.portal.portlet.om.IPortletWindow;
import org.jasig.portal.portlet.om.IPortletWindowId;
import org.jasig.portal.portlet.registry.IPortletDefinitionRegistry;
import org.jasig.portal.portlet.registry.IPortletEntityRegistry;
import org.jasig.portal.portlet.registry.ITransientPortletWindowRegistry;
import org.jasig.portal.security.IPerson;
import org.jasig.portal.url.IPortalRequestInfo;
import org.jasig.portal.url.IPortalUrlProvider;
import org.jasig.portal.url.IPortletPortalUrl;
import org.jasig.portal.user.IUserInstance;
import org.jasig.portal.user.IUserInstanceManager;
import org.jasig.portal.utils.Tuple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Contains the logic and string constants for generating and parsing portlet URL parameters.
 * 
 * @author Eric Dalquist
 * @version $Revision$
 */
@Component
public class PortletUrlSyntaxProviderImpl implements IPortletUrlSyntaxProvider {

	private static final String SEPARATOR = "_";
	private static final String PORTLET_CONTROL_PREFIX = "pltc" + SEPARATOR;
	private static final String PORTLET_PARAM_PREFIX = "pltp" + SEPARATOR;

	private static final String PARAM_REQUEST_TARGET = PORTLET_CONTROL_PREFIX + "target";
	private static final String PARAM_REQUEST_TYPE = PORTLET_CONTROL_PREFIX + "type";
	private static final String PARAM_WINDOW_STATE = PORTLET_CONTROL_PREFIX + "state";
	private static final String PARAM_PORTLET_MODE = PORTLET_CONTROL_PREFIX + "mode";

	private static final Pattern URL_PARAM_NAME = Pattern.compile("&([^&?=\n]*)");

	protected final Log logger = LogFactory.getLog(this.getClass());

	private String defaultEncoding = "UTF-8";
	private IPortalUrlProvider portalUrlProvider;
	private ITransientPortletWindowRegistry portletWindowRegistry;
	private IUserInstanceManager userInstanceManager;
	private IPortletDefinitionRegistry portletDefinitionRegistry;
	private IPortletEntityRegistry portletEntityRegistry;

	/**
	 * @param defaultEncoding the defaultEncoding to set
	 */
	public void setDefaultEncoding(String defaultEncoding) {
		Validate.notEmpty(defaultEncoding, "defaultEncoding cannot be empty");
		this.defaultEncoding = defaultEncoding;
	}
	/**
	 * @param portalUrlProvider the portalUrlProvider to set
	 */
	@Autowired
	public void setPortalUrlProvider(final IPortalUrlProvider portalUrlProvider) {
		this.portalUrlProvider = portalUrlProvider;
	}
	/**
	 * @param portletWindowRegistry the portletWindowRegistry to set
	 */
	@Autowired
	public void setPortletWindowRegistry(
			ITransientPortletWindowRegistry portletWindowRegistry) {
		this.portletWindowRegistry = portletWindowRegistry;
	}
	/**
	 * @param userInstanceManager the userInstanceManager to set
	 */
	@Autowired
	public void setUserInstanceManager(IUserInstanceManager userInstanceManager) {
		this.userInstanceManager = userInstanceManager;
	}
	/**
	 * @param portletDefinitionRegistry the portletDefinitionRegistry to set
	 */
	@Autowired
	public void setPortletDefinitionRegistry(
			IPortletDefinitionRegistry portletDefinitionRegistry) {
		this.portletDefinitionRegistry = portletDefinitionRegistry;
	}
	/**
	 * @param portletEntityRegistry the portletEntityRegistry to set
	 */
	@Autowired
	public void setPortletEntityRegistry(
			IPortletEntityRegistry portletEntityRegistry) {
		this.portletEntityRegistry = portletEntityRegistry;
	}
	/* (non-Javadoc)
	 * @see org.jasig.portal.portlet.url.IPortletUrlSyntaxProvider#generatePortletUrl(javax.servlet.http.HttpServletRequest, org.jasig.portal.portlet.om.IPortletWindow, org.jasig.portal.portlet.url.PortletUrl)
	 */
	public String generatePortletUrl(HttpServletRequest request,
			IPortletWindow portletWindow, PortletUrl portletUrl) {
		IPortletPortalUrl portalPortletUrl = portalUrlProvider.getPortletUrl(request, portletWindow.getPortletWindowId());
		portalPortletUrl = mergeWithPortletUrl(portalPortletUrl, portletUrl);
		return portalPortletUrl.toString();
	}

	/*
	 * (non-Javadoc)
	 * @see org.jasig.portal.portlet.url.IPortletUrlSyntaxProvider#parsePortletUrl(javax.servlet.http.HttpServletRequest)
	 */
	public PortletUrl parsePortletUrl(HttpServletRequest request) {
		IPortalRequestInfo requestInfo = portalUrlProvider.getPortalRequestInfo(request);
		if(null == requestInfo.getTargetedPortletWindowId()) {
			return null;
		} else {
			IPortletWindowId portletWindowId = requestInfo.getTargetedPortletWindowId(); 
			IPortletPortalUrl portalPortletUrl = portalUrlProvider.getPortletUrl(request, portletWindowId);
			return toPortletUrl(portletWindowId, portalPortletUrl);
		}
	}

	/**
	 * Convert a {@link IPortalPortletUrl} into a {@link PortletUrl}.
	 * 
	 * @param portalPortletUrl
	 * @return
	 */
	protected static PortletUrl toPortletUrl(IPortletWindowId portletWindowId, IPortletPortalUrl portalPortletUrl) {
		PortletUrl result = new PortletUrl(portletWindowId);
		Map<String, List<String>> parameters = portalPortletUrl.getPortletParameters();
		result.setParameters(parameters);

		result.setPortletMode(portalPortletUrl.getPortletMode());

		if(portalPortletUrl.isAction()) {
			result.setRequestType(PortletURLProvider.TYPE.ACTION);
		} else {
			result.setRequestType(PortletURLProvider.TYPE.RENDER);
		}

		// null is the default value for the secure field
		//result.setSecure(null);

		result.setWindowState(portalPortletUrl.getWindowState());
		return result;
	}

	/**
	 * The purpose of this method is to port the fields of the {@link PortletUrl} argument
	 * to the appropriate fields of the {@link IPortletPortalUrl} argument.
	 * 
	 * This method mutates the {@link IPortletPortalUrl} argument and return it.
	 * 
	 * Neither argument can be null.
	 * 
	 * @param original
	 * @param mergeWith
	 * @return the updated original {@link IPortalPortletUrl}
	 */
	protected static IPortletPortalUrl mergeWithPortletUrl(IPortletPortalUrl original, PortletUrl mergeWith) {
		Validate.notNull(original, "original IPortalPortletUrl must not be null");
		Validate.notNull(mergeWith, "mergeWith PortletUrl must not be null");
		if (PortletURLProvider.TYPE.ACTION == mergeWith.getRequestType()) {
			original.setAction(true);
		}
		original.setPortletMode(mergeWith.getPortletMode());

		original.setWindowState(mergeWith.getWindowState());

		final Map<String, List<String>> mergeParameters = mergeWith.getParameters();
		for (final Map.Entry<String, List<String>> mergeParameter : mergeParameters.entrySet()) {
			original.setPortalParameter(mergeParameter.getKey(), mergeParameter.getValue());
		}
		return original;
	}

	/*
	 * (non-Javadoc)
	 * @see org.jasig.portal.portlet.url.IPortletUrlSyntaxProvider#parsePortletParameters(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	public Tuple<IPortletWindowId, PortletUrl> parsePortletParameters(
			HttpServletRequest request) {
		Validate.notNull(request, "request can not be null");

		final IPortletWindowId targetedPortletWindowId;

		final String targetedPortletWindowIdStr = request.getParameter(PARAM_REQUEST_TARGET);
		if (targetedPortletWindowIdStr != null) {
			targetedPortletWindowId = this.portletWindowRegistry.getPortletWindowId(targetedPortletWindowIdStr);
		}
		else {
			//Fail over to looking for a fname
			final String targetedFname = request.getParameter("uP_fname");
			if (targetedFname == null) {
				return null;
			}

			//Get the user's layout manager
			final IUserInstance userInstance = this.userInstanceManager.getUserInstance(request);
			final IUserPreferencesManager preferencesManager = userInstance.getPreferencesManager();
			final IUserLayoutManager userLayoutManager = preferencesManager.getUserLayoutManager();

			//Determine the subscribe ID
			final String channelSubscribeId = userLayoutManager.getSubscribeId(targetedFname);
			if (channelSubscribeId == null) {
				this.logger.info("No channel subscribe ID found for fname '" + targetedFname + "'. skipping portlet parameter processing");
				return null;
			}

			//Find the channel and portlet definitions
			final IUserLayoutChannelDescription channelNode = (IUserLayoutChannelDescription)userLayoutManager.getNode(channelSubscribeId);
			final String channelPublishId = channelNode.getChannelPublishId();
			final IPortletDefinition portletDefinition = this.portletDefinitionRegistry.getPortletDefinition(Integer.parseInt(channelPublishId));
			if (portletDefinition == null) {
				this.logger.info("No portlet defintion found for channel definition '" + channelPublishId + "' with fname '" + targetedFname + "'. skipping portlet parameter processing");
				return null;
			}

			//Determine the appropriate portlet window ID
			final IPerson person = userInstance.getPerson();
			final IPortletEntity portletEntity = this.portletEntityRegistry.getOrCreatePortletEntity(portletDefinition.getPortletDefinitionId(), channelSubscribeId, person.getID());
			final IPortletWindow defaultPortletWindow = this.portletWindowRegistry.createDefaultPortletWindow(request, portletEntity.getPortletEntityId());
			targetedPortletWindowId = this.portletWindowRegistry.createTransientPortletWindowId(request, defaultPortletWindow.getPortletWindowId());
		}

		final PortletUrl portletUrl = new PortletUrl(targetedPortletWindowId);

		final String requestTypeStr = request.getParameter(PARAM_REQUEST_TYPE);
		if (requestTypeStr != null) {
			final TYPE requestType = TYPE.valueOf(requestTypeStr);
			portletUrl.setRequestType(requestType);
		}
		else {
			//Default to RENDER request if no request type was specified
			portletUrl.setRequestType(TYPE.RENDER);
		}

		final String windowStateStr = request.getParameter(PARAM_WINDOW_STATE);
		if (windowStateStr != null) {
			final WindowState windowState = new WindowState(windowStateStr);
			portletUrl.setWindowState(windowState);
		}

		final String portletModeStr = request.getParameter(PARAM_PORTLET_MODE);
		if (portletModeStr != null) {
			final PortletMode portletMode = new PortletMode(portletModeStr);
			portletUrl.setPortletMode(portletMode);
		}

		final Map<String, String[]> requestParameters = request.getParameterMap();
		final Set<String> urlParameterNames = this.getUrlParameterNames(request);

		final Map<String, List<String>> portletParameters = new HashMap<String, List<String>>(requestParameters.size());
		for (final Map.Entry<String, String[]> parameterEntry : requestParameters.entrySet()) {
			final String parameterName = parameterEntry.getKey();

			//If the parameter starts with the param prefix add it to the Map
			if (parameterName.startsWith(PORTLET_PARAM_PREFIX)) {
				final String portletParameterName = parameterName.substring(PORTLET_PARAM_PREFIX.length());
				final String[] portletParameterValues = parameterEntry.getValue();

				portletParameters.put(portletParameterName, Arrays.asList(portletParameterValues));
			}
			//If it did not appear on the URL it must be a submit parameter so add it to the Map
			else if (urlParameterNames != null && !urlParameterNames.contains(parameterName)) {
				final String[] portletParameterValues = parameterEntry.getValue();

				portletParameters.put(parameterName, Arrays.asList(portletParameterValues));
			}
		}
		portletUrl.setParameters(portletParameters);

		portletUrl.setSecure(request.isSecure());

		return new Tuple<IPortletWindowId, PortletUrl>(targetedPortletWindowId, portletUrl);
	}

	/**
	 * Parses the request URL to return a Set of the parameter names that appeared on the URL string.
	 * 
	 * @param request The request to look at.
	 * @return The Set of parameter names from the URL.
	 */
	protected Set<String> getUrlParameterNames(HttpServletRequest request) {
		// Only posts can have parameters not in the URL, ignore non-post requests.
		final String method = request.getMethod();
		if (!"POST".equals(method)) {
			return null;
		}

		final Set<String> urlParameterNames = new HashSet<String>();

		final String queryString = request.getQueryString();
		final Matcher paramNameMatcher = URL_PARAM_NAME.matcher("&" + queryString);

		final String encoding = this.getEncoding(request);

		while (paramNameMatcher.find()) {
			final String paramName = paramNameMatcher.group(1);
			String decParamName;
			try {
				decParamName = URLDecoder.decode(paramName, encoding);
			}
			catch (UnsupportedEncodingException uee) {
				decParamName = paramName;
			}

			urlParameterNames.add(decParamName);
		}

		return urlParameterNames;
	}

	/**
	 * Tries to determine the encoded from the request, if not available falls back to configured default.
	 * 
	 * @param request The current request.
	 * @return The encoding to use.
	 */
	protected String getEncoding(HttpServletRequest request) {
		final String encoding = request.getCharacterEncoding();
		if (encoding != null) {
			return encoding;
		}

		return this.defaultEncoding;
	}

}
