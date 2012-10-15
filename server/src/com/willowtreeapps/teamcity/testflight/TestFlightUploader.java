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

package com.willowtreeapps.teamcity.testflight;

import jetbrains.buildServer.log.Loggers;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.simple.parser.JSONParser;

import java.io.*;
import java.util.Map;
import java.util.Scanner;

/**
 * Created by IntelliJ IDEA.
 * User: jbeck
 * Date: 10/11/12
 * Time: 4:03 PM
 */
public class TestFlightUploader implements Serializable
{
    public static class UploadRequest implements Serializable
    {
        public String apiToken;
        public String teamToken;
        public Boolean notifyTeam;
        public String buildNotes;
        public File file;
        public File dsymFile;
        public String lists;
        public Boolean replace;
        public String proxyHost;
        public String proxyUser;
        public String proxyPass;
        public int proxyPort;
    }

    public Map upload(UploadRequest ur) throws IOException, org.json.simple.parser.ParseException {

        DefaultHttpClient httpClient = new DefaultHttpClient();

        // Configure the proxy if necessary
        if(ur.proxyHost!=null && !ur.proxyHost.isEmpty() && ur.proxyPort>0)
        {
            Credentials cred = null;

            if(ur.proxyUser!=null && !ur.proxyUser.isEmpty())
            {
                cred = new UsernamePasswordCredentials(ur.proxyUser, ur.proxyPass);
            }

            httpClient.getCredentialsProvider().setCredentials(new AuthScope(ur.proxyHost, ur.proxyPort),cred);
            HttpHost proxy = new HttpHost(ur.proxyHost, ur.proxyPort);
            httpClient.getParams().setParameter( ConnRoutePNames.DEFAULT_PROXY, proxy);
        }

        HttpHost targetHost = new HttpHost("testflightapp.com");
        HttpPost httpPost = new HttpPost("/api/builds.json");
        FileBody fileBody = new FileBody(ur.file);

        MultipartEntity entity = new MultipartEntity();
        entity.addPart("api_token", new StringBody(ur.apiToken));
        entity.addPart("team_token", new StringBody(ur.teamToken));
        entity.addPart("notes", new StringBody(ur.buildNotes));
        entity.addPart("file", fileBody);

        if (ur.dsymFile != null)
        {
            FileBody dsymFileBody = new FileBody(ur.dsymFile);
            entity.addPart("dsym", dsymFileBody);
        }

        if (ur.lists.length() > 0)
        {
            entity.addPart("distribution_lists", new StringBody(ur.lists));
        }

        entity.addPart("notify", new StringBody(ur.notifyTeam ? "True" : "False"));
        entity.addPart("replace", new StringBody(ur.replace ? "True" : "False"));
        httpPost.setEntity(entity);

        HttpResponse response = httpClient.execute(targetHost,httpPost);
        HttpEntity resEntity = response.getEntity();

        InputStream is = resEntity.getContent();

        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode != 200)
        {
            String responseBody = new Scanner(is).useDelimiter("\\A").next();
            throw new UploadException(statusCode, responseBody, response);
        }
        else
        {
            Loggers.SERVER.info("'" + ur.file.getName() + "' successfully uploaded to TestFlight");
        }

        JSONParser parser = new JSONParser();

        return (Map)parser.parse(new BufferedReader(new InputStreamReader(is)));
    }
}
