/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.webtools;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.*;
import org.ofbiz.base.util.cache.UtilCache;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.security.Security;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

/**
 * Contains events for the UtilCache class; must be external to access security resources
 */
public final class UtilCacheEvents {

    public static final String err_resource = "WebtoolsErrorUiLabels";

    private UtilCacheEvents() {}

    /** An HTTP WebEvent handler the specified element from the specified cache
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return return an HTTP WebEvent handler the specified element from the specified cache
     */
    public static String removeElementEvent(HttpServletRequest request, HttpServletResponse response) {
        String errMsg = "";
        Locale locale = UtilHttp.getLocale(request);

        Security security = (Security) request.getAttribute("security");
        if (!security.hasPermission("UTIL_CACHE_EDIT", request)) { // SCIPIO: Now using request; was: request.getSession()
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.permissionEdit", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        String name = request.getParameter("UTIL_CACHE_NAME");
        if (name == null) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.noCacheNameSpecified", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        String numString = request.getParameter("UTIL_CACHE_ELEMENT_NUMBER");

        if (numString == null) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.noElementNumberSpecified", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", "");
            return "error";
        }
        int number;

        try {
            number = Integer.parseInt(numString);
        } catch (Exception e) {
            return "error";
        }

        UtilCache<?, ?> utilCache = UtilCache.findCache(name);

        if (utilCache != null) {
            Object key = null;

            Iterator<?> ksIter = utilCache.getCacheLineKeys().iterator();
            int curNum = 0;

            while (ksIter.hasNext()) {
                if (number == curNum) {
                    key = ksIter.next();
                    break;
                } else {
                    ksIter.next();
                }
                curNum++;
            }

            if (key != null) {
                utilCache.remove(key);
                errMsg = UtilProperties.getMessage(err_resource, "utilCache.removeElementWithKey", UtilMisc.toMap("key", key.toString()), locale) + ".";
                request.setAttribute("_EVENT_MESSAGE_", errMsg);
            } else {
                errMsg = UtilProperties.getMessage(err_resource, "utilCache.couldNotRemoveElementNumber", UtilMisc.toMap("name", name, "numString", numString), locale) + ".";
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } else {
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.couldNotRemoveElement", UtilMisc.toMap("name", name), locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        return "success";
    }

    /** An HTTP WebEvent handler that clears the named cache
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return return an HTTP WebEvent handler that clears the named cache
     */
    public static String clearEvent(HttpServletRequest request, HttpServletResponse response) {
        String errMsg = "";
        Locale locale = UtilHttp.getLocale(request);

        Security security = (Security) request.getAttribute("security");
        if (!security.hasPermission("UTIL_CACHE_EDIT", request)) { // SCIPIO: Now using request; was: request.getSession()
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.permissionEdit", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        String name = request.getParameter("UTIL_CACHE_NAME");

        if (name == null) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.couldNotClearCache", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        UtilCache<?, ?> utilCache = UtilCache.findCache(name);

        if (utilCache != null) {
            utilCache.clear();
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.clearCache", UtilMisc.toMap("name", name), locale) + ".";
            request.setAttribute("_EVENT_MESSAGE_", errMsg);
        } else {
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.couldNotClearCacheNotFoundName", UtilMisc.toMap("name", name), locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        return "success";
    }

    /** An HTTP WebEvent handler that clears all caches
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return return an HTTP WebEvent handler that clears all caches
     */
    public static String clearAllEvent(HttpServletRequest request, HttpServletResponse response) {
        String errMsg = "";
        HttpSession session = request.getSession();
        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        Locale locale = UtilHttp.getLocale(request);

        Security security = (Security) request.getAttribute("security");
        if (!security.hasPermission("UTIL_CACHE_EDIT", request)) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.permissionEdit", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }

        //SCIPIO: Added new call to the distributed cacheClear
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Map<String, Object> results = null;
        try {
            results = dispatcher.runSync("clearAllUtilCaches", UtilMisc.toMap("userLogin", userLogin, "distribute", true));
            if (ServiceUtil.isError(results)) {
                String errorMessage = ServiceUtil.getErrorMessage(results);
                errMsg = UtilProperties.getMessage(err_resource, "utilCache.clearAllCaches", locale);
                return "error";
            }
        } catch (GenericServiceException e) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.clearAllCaches", locale);
            return "error";
        }

        errMsg = UtilProperties.getMessage(err_resource, "utilCache.clearAllCaches", locale);
        request.setAttribute("_EVENT_MESSAGE_", errMsg + " (" + UtilDateTime.nowDateString("yyyy-MM-dd HH:mm:ss")  + ").");
        return "success";
    }

    /** An HTTP WebEvent handler that updates the named cache
     * @param request The HTTP request object for the current JSP or Servlet request.
     * @param response The HTTP response object for the current JSP or Servlet request.
     * @return return an HTTP WebEvent handler that updates the named cache
     */
    public static String updateEvent(HttpServletRequest request, HttpServletResponse response) {
        String errMsg = "";
        Locale locale = UtilHttp.getLocale(request);

        Security security = (Security) request.getAttribute("security");
        if (!security.hasPermission("UTIL_CACHE_EDIT", request)) { // SCIPIO: Now using request; was: request.getSession()
            errMsg = UtilProperties.getMessage(err_resource, "utilCacheEvents.permissionEdit", locale) + ".";
            request.setAttribute("_EVENT_MESSAGE_", errMsg);
            return "error";
        }

        String name = request.getParameter("UTIL_CACHE_NAME");

        if (name == null) {
            errMsg = UtilProperties.getMessage(err_resource, "utilCache.couldNotUpdateCacheSetting", locale) + ".";
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        }
        String maxInMemoryStr = request.getParameter("UTIL_CACHE_MAX_IN_MEMORY");
        String expireTimeStr = request.getParameter("UTIL_CACHE_EXPIRE_TIME");
        String useSoftReferenceStr = request.getParameter("UTIL_CACHE_USE_SOFT_REFERENCE");
        String maxSizeStr = request.getParameter("UTIL_CACHE_MAX_SIZE"); // SCIPIO: added 2017-05-23

        Integer maxInMemory = null;
        Long expireTime = null;
        Integer maxSize = null;

        try {
            maxInMemory = Integer.valueOf(maxInMemoryStr);
        } catch (Exception e) {}
        try {
            expireTime = Long.valueOf(expireTimeStr);
        } catch (Exception e) {}
        try {
            maxSize = Integer.valueOf(maxSizeStr);
        } catch (Exception e) {}

        UtilCache<?, ?> utilCache = UtilCache.findCache(name);

        if (utilCache != null) {
            if (maxInMemory != null)
                utilCache.setMaxInMemory(maxInMemory);
            if (expireTime != null)
                utilCache.setExpireTime(expireTime);
            if (useSoftReferenceStr != null) {
                utilCache.setUseSoftReference("true".equals(useSoftReferenceStr));
            }
            if (maxSize != null) {
                utilCache.setSizeLimit(maxSize);
            }
        }
        return "success";
    }
}
