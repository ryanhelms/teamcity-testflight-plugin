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

package com.willowtreeapps.teamcity.server;

import com.willowtreeapps.teamcity.testflight.TestFlightUploader;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifact;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifacts;
import jetbrains.buildServer.serverSide.artifacts.BuildArtifactsViewMode;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import com.willowtreeapps.teamcity.common.Util;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.Map;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * Created by IntelliJ IDEA.
 * User: jbeck
 * Date: 10/11/12
 * Time: 4:03 PM
 */
public class ServerListener extends BuildServerAdapter
{

    private static final String POM = "pom.xml";

    private static final String API_TOKEN = "testflight.api.token";

    private static final String TEAM_TOKEN = "testflight.team.token";

    private static final String DISTRIBUTION = "testflight.distribution";

    private static final String CLIENT = "client";

    private static final String CLIENT_IPA = CLIENT + ".ipa";

    private static final String PROFILE = "profile";

    private static final String ID = "id";

    private String apiToken;

    private String teamToken;

    private String distroLists;

    private SBuildServer myServer;

    public ServerListener(@NotNull final EventDispatcher<BuildServerListener> dispatcher, SBuildServer server)
    {
        dispatcher.addListener(this);
        myServer = server;
    }

    @Override
    public void serverStartup()
    {
        Loggers.SERVER.info("Plugin '" + Util.NAME + "'. Is running on server version " + myServer.getFullServerVersion() + ".");
    }

    @Override
    public void buildPinned(final @NotNull SBuild build, final @Nullable User user, final @Nullable String comment)
    {
        BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);

        BuildArtifact pom = artifacts.getArtifact(POM);
        BuildArtifact client = artifacts.getArtifact(CLIENT_IPA);

        Map responseEntity = null;

        try
        {
            this.processPom(pom);

            TestFlightUploader uploader = new TestFlightUploader();
            TestFlightUploader.UploadRequest request = new TestFlightUploader.UploadRequest();
            request.apiToken = this.apiToken;
            request.teamToken = this.teamToken;
            request.buildNotes = comment;
            request.lists = distroLists;
            request.notifyTeam = true;
            request.replace = true;

            if (client == null)
            {
                Loggers.SERVER.info("Nothing to upload (i.e. client.ipa doesn't exist)");
                return;
            }

            request.file = this.extractFile(client);
            responseEntity = uploader.upload(request);

            if (request.file.delete())
            {
                Loggers.SERVER.info("Tmp file deleted");
            }
        }
        catch (Exception e)
        {
            Loggers.SERVER.error("Error processing POM", e);
        }
        finally
        {
            String subject = "TestFlight upload results: '" + build.getFullName() + "', '" + build.getBuildNumber() + "'";

            this.notifyPinner(user, null, subject, this.processResponseEntity(responseEntity));
        }
    }

    @Override
    public void buildUnpinned(final @NotNull SBuild build, final @Nullable User user, final @Nullable String comment)
    {
        StringBuilder builder = new StringBuilder("Notifying you (and everyone else) that you unpinned a build");
        builder.append("\nUser Comment:\n");
        builder.append(comment);

        this.notifyPinner(user, "notice@willowtreeapps.com", "You unpinned build: '" + build.getFullName() + "', '" + build.getBuildNumber() + "'", builder.toString());
    }

    private String processResponseEntity(Map responseEntity)
    {
        StringBuilder builder = new StringBuilder();

        if (responseEntity != null)
        {
            for (Object key : responseEntity.keySet())
            {
                builder.append(key.toString());
                builder.append(":");
                builder.append(responseEntity.get(key).toString());
                builder.append("\n");
            }

            builder.append("\nGov.");
        }
        else
        {
            builder.append("It is likely that something went wrong.  Please consult the logs, Gov.");
        }

        return builder.toString();
    }

    private void notifyPinner(User user, @Nullable String cc, String subject, String content)
    {
        Properties props = System.getProperties();
        props.setProperty("mail.smtp.host", "localhost");

        Session session = Session.getDefaultInstance(props);

        try
        {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress("ci@willowtreeapps.com"));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(user.getEmail()));
            if (cc != null) {
                message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc));
            }
            message.setSubject(subject);
            message.setText(content);

            Transport.send(message);
            Loggers.SERVER.info("User '" + user.getName() + "' notified");
        }
        catch (MessagingException e)
        {
            Loggers.SERVER.error(e.getMessage(), e);
        }
    }

    private void processPom(BuildArtifact pom) throws ParserConfigurationException, SAXException, IOException
    {
        InputStream inputStream = pom.getInputStream();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser = factory.newSAXParser();

        parser.parse(inputStream, new DefaultHandler()
        {
            boolean isProfileId = false;
            boolean isClientProfile = false;
            boolean isApiToken = false;
            boolean isTeamToken = false;
            boolean isDistro = false;

            public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
            {
                if (qName.equals(ID))
                {
                    isProfileId = true;
                }
                else if (qName.equals(API_TOKEN) && isClientProfile)
                {
                    isApiToken = true;
                }
                else if (qName.equals(TEAM_TOKEN) && isClientProfile)
                {
                    isTeamToken = true;
                }
                else if (qName.equals(DISTRIBUTION) && isClientProfile)
                {
                    isDistro = true;
                }
            }

            public void characters(char ch[], int start, int length) throws SAXException
            {
                if (isProfileId)
                {
                    String profileName = new String(ch).substring(start, start + length);

                    if (profileName.equalsIgnoreCase(CLIENT))
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
                if (qName.equals(PROFILE))
                {
                    isClientProfile = false;
                }
            }
        });
    }

    private File extractFile(BuildArtifact artifact) throws IOException
    {
        File file = File.createTempFile("client", ".ipa");

        InputStream in = artifact.getInputStream();
        OutputStream out = new FileOutputStream(file);

        byte buffer[] = new byte[1024];
        int length;

        while ((length = in.read(buffer)) > 0)
        {
            out.write(buffer, 0, length);
        }

        in.close();
        out.close();

        return file;
    }
}
