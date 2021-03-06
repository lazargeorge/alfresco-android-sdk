/*******************************************************************************
 * Copyright (C) 2005-2012 Alfresco Software Limited.
 * 
 * This file is part of the Alfresco Mobile SDK.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package org.alfresco.mobile.android.api.model;

/**
 * List of search language available inside the SDK.
 * 
 * @author Jean Marie Pascal
 */
public enum SearchLanguage
{
    CMIS("CMIS");

    private final String value;

    SearchLanguage(String v)
    {
        value = v;
    }

    public String value()
    {
        return value;
    }

    public static SearchLanguage fromValue(String v)
    {
        for (SearchLanguage c : SearchLanguage.values())
        {
            if (c.value.equals(v)) { return c; }
        }
        throw new IllegalArgumentException(v);
    }
}