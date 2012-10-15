/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.willowtreeapps.teamcity;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Created by IntelliJ IDEA.
 * User: jbeck
 * Date: 10/15/12
 * Time: 9:44 AM
 */
public class PomHandler extends DefaultHandler
{
    private String apiToken;

    private String teamToken;

    private String distroLists;

    private boolean isProfileId = false;

    private boolean isClientProfile = false;

    private boolean isApiToken = false;

    private boolean isTeamToken = false;

    private boolean isDistro = false;

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
    {
        if (qName.equals(Constants.ID))
        {
            isProfileId = true;
        }
        else if (qName.equals(Constants.API_TOKEN) && isClientProfile)
        {
            isApiToken = true;
        }
        else if (qName.equals(Constants.TEAM_TOKEN) && isClientProfile)
        {
            isTeamToken = true;
        }
        else if (qName.equals(Constants.DISTRIBUTION) && isClientProfile)
        {
            isDistro = true;
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException
    {
        if (isProfileId)
        {
            String profileName = new String(ch).substring(start, start + length);

            if (profileName.equalsIgnoreCase(Constants.CLIENT))
            {
                isClientProfile = true;
            }
            isProfileId = false;
        }
        else if (isApiToken && isClientProfile)
        {
            apiToken = new String(ch).substring(start, start + length);
            isApiToken = false;
        }
        else if (isTeamToken && isClientProfile)
        {
            teamToken = new String(ch).substring(start, start + length);
            isTeamToken = false;
        }
        else if (isDistro && isClientProfile)
        {
            distroLists = new String(ch).substring(start, start + length);
            isDistro = false;
        }
    }

    public void endElement(String uri, String localName, String qName) throws SAXException
    {
        if (qName.equals(Constants.PROFILE))
        {
            isClientProfile = false;
        }
    }

    public String getApiToken()
    {
        return apiToken;
    }

    public String getTeamToken()
    {
        return teamToken;
    }

    public String getDistroLists()
    {
        return distroLists;
    }
}
