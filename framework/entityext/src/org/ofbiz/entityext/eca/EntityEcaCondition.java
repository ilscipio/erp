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
package org.ofbiz.entityext.eca;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.ofbiz.base.GeneralConfig;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.ObjectType;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.util.EntityUtilProperties;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Element;

/**
 * EntityEcaCondition
 */
@SuppressWarnings("serial")
public final class EntityEcaCondition implements java.io.Serializable {

    private static final Debug.OfbizLogger module = Debug.getOfbizLogger(java.lang.invoke.MethodHandles.lookup().lookupClass());

    protected String lhsValueName = null;
    protected String rhsValueName = null;
    protected String operator = null;
    protected String compareType = null;
    protected String format = null;
    protected boolean constant = false;
    protected boolean isService = false;
    protected String conditionService = null;
    protected boolean property = false; // SCIPIO
    protected String propertyResource = null;

    public EntityEcaCondition(Element condition, boolean constant, boolean isService, boolean property) { // SCIPIO: added property
        if (isService) {
            this.isService = isService;
            this.conditionService = condition.getAttribute("service-name");
        } else {
            this.lhsValueName = condition.getAttribute("field-name");
            this.constant = constant;
            if (constant) {
                this.rhsValueName = condition.getAttribute("value");
            } else {
                this.rhsValueName = condition.getAttribute("to-field-name");
            }
            this.operator = condition.getAttribute("operator");
            this.compareType = condition.getAttribute("type");
            this.format = condition.getAttribute("format");
            this.property = property; // SCIPIO
            this.propertyResource = condition.getAttribute("resource");
            if (property) {
                this.lhsValueName = condition.getAttribute("property-name");
                int splitIndex = this.lhsValueName.indexOf('#');
                if (this.propertyResource.isEmpty() && splitIndex >= 1) {
                    this.propertyResource = this.lhsValueName.substring(0, splitIndex);
                    this.lhsValueName = this.lhsValueName.substring(splitIndex + 1);
                }
                if (this.propertyResource.isEmpty()) {
                    this.propertyResource = null;
                }
            }
        }
    }

    public EntityEcaCondition(Element condition, boolean constant, boolean isService) {
        this(condition, constant, isService, false);
    }

    public boolean eval(DispatchContext dctx, GenericEntity value, Map<String, Object> context) throws GenericEntityException {
        if (dctx == null || value == null || dctx.getClassLoader() == null) {
            throw new GenericEntityException("Cannot have null Value or DispatchContext!");
        }

        if (Debug.verboseOn()) Debug.logVerbose(this.toString(), module);

        // condition-service; run the service and return the reply result
        if (isService) {
            try {
                LocalDispatcher dispatcher = dctx.getDispatcher();
                Map<String, Object> conditionServiceResult = dispatcher.runSync(conditionService,
                        UtilMisc.<String, Object>toMap("serviceContext", context, "userLogin", context.get("userLogin")));

                Boolean conditionReply = Boolean.FALSE;
                if (ServiceUtil.isError(conditionServiceResult)) {
                    Debug.logError("Error in condition-service : " +
                            ServiceUtil.getErrorMessage(conditionServiceResult), module);
                } else {
                    conditionReply = (Boolean) conditionServiceResult.get("conditionReply");
                }
                return conditionReply;
            } catch (GenericServiceException gse) {
                throw new GenericEntityException("Error in calling condition service "+conditionService+". "+gse.getMessage());
            }
        }

        // SCIPIO
        //Object lhsValue = value.get(lhsValueName);
        Object lhsValue;
        if (property) {
            if (propertyResource != null) {
                lhsValue = EntityUtilProperties.getPropertyValue(propertyResource, lhsValueName, dctx.getDelegator());
            } else {
                lhsValue = GeneralConfig.getCommonPropertiesMap().get(lhsValueName);
                if (lhsValue == null) {
                    Debug.logWarning("Could not find property named '" + lhsValueName
                            + "' in GeneralConfig.getCommonPropertiesMap for eca (invalid common name or missing resource); returning false", module);
                    return false;
                }
            }
        } else {
            lhsValue = value.get(lhsValueName);
        }

        Object rhsValue;
        if (constant) {
            rhsValue = rhsValueName;
        } else {
            rhsValue = value.get(rhsValueName);
        }

        if (Debug.verboseOn()) Debug.logVerbose("Comparing : " + lhsValue + " " + operator + " " + rhsValue, module);

        // evaluate the condition & invoke the action(s)
        List<Object> messages = new LinkedList<Object>();
        Boolean cond = ObjectType.doRealCompare(lhsValue, rhsValue, operator, compareType, format, messages, null, dctx.getClassLoader(), constant);

        // if any messages were returned send them out
        if (messages.size() > 0) {
            for (Object message: messages) {
                Debug.logWarning((String) message, module);
            }
        }
        if (cond != null) {
            return cond;
        } else {
            return false;
        }
    }

    public String getLValue() {
        return this.lhsValueName;
    }

    public String getRValue() {
        if (constant && !rhsValueName.isEmpty()) {
            return "\"".concat(this.rhsValueName).concat("\"");
        }
        return this.rhsValueName;
    }

    public String getOperator() {
        return this.operator;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        if (UtilValidate.isNotEmpty(conditionService)) buf.append("[").append(conditionService).append("]");
        if (UtilValidate.isNotEmpty(lhsValueName)) buf.append("[").append(lhsValueName).append("]");
        if (UtilValidate.isNotEmpty(operator)) buf.append("[").append(operator).append("]");
        if (UtilValidate.isNotEmpty(rhsValueName)) buf.append("[").append(rhsValueName).append("]");
        if (UtilValidate.isNotEmpty(constant)) buf.append("[").append(constant).append("]");
        if (UtilValidate.isNotEmpty(compareType)) buf.append("[").append(compareType).append("]");
        if (UtilValidate.isNotEmpty(format)) buf.append("[").append(format).append("]");
        if (UtilValidate.isNotEmpty(format)) buf.append("[").append(format).append("]"); // SCIPIO
        return buf.toString();
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((lhsValueName == null) ? 0 : lhsValueName.hashCode());
        result = prime * result + ((operator == null) ? 0 : operator.hashCode());
        result = prime * result + ((rhsValueName == null) ? 0 : rhsValueName.hashCode());
        result = prime * result + (constant ? 1231 : 1237);
        result = prime * result + ((compareType == null) ? 0 : compareType.hashCode());
        result = prime * result + ((format == null) ? 0 : format.hashCode());
        // SCIPIO: FIXME: should more fields be here, or is this hashcode intended to differ from equals?? (unusual)
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityEcaCondition) {
            EntityEcaCondition other = (EntityEcaCondition) obj;

            if (!UtilValidate.areEqual(this.conditionService, other.conditionService)) return false;
            if (!UtilValidate.areEqual(this.lhsValueName, other.lhsValueName)) return false;
            if (!UtilValidate.areEqual(this.rhsValueName, other.rhsValueName)) return false;
            if (!UtilValidate.areEqual(this.operator, other.operator)) return false;
            if (!UtilValidate.areEqual(this.compareType, other.compareType)) return false;
            if (!UtilValidate.areEqual(this.format, other.format)) return false;
            if (this.constant != other.constant) return false;
            if (this.isService != other.isService) return false;
            if (this.property != other.property) return false; // SCIPIO
            if (!UtilValidate.areEqual(this.propertyResource, other.propertyResource)) return false; // SCIPIO

            return true;
        } else {
            return false;
        }
    }

    protected List<String> getFieldNames() {
        List<String> fieldNameList = new ArrayList<String>();
        if( UtilValidate.isNotEmpty(lhsValueName) ) {
            fieldNameList.add(lhsValueName);
        }
        if(!constant && UtilValidate.isNotEmpty(rhsValueName)) {
            fieldNameList.add(rhsValueName);
        }
        return fieldNameList;
    }

}
