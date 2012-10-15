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

import com.willowtreeapps.teamcity.Constants;
import com.willowtreeapps.teamcity.PomHandler;
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
    private SBuildServer myServer;

    private PomHandler pomHandler;

    private BuildArtifact pom;

    private BuildArtifact client;

    public ServerListener(@NotNull final EventDispatcher<BuildServerListener> dispatcher, SBuildServer server)
    {
        dispatcher.addListener(this);
        myServer = server;
        pomHandler = new PomHandler();
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

        pom = artifacts.getArtifact(Constants.POM);
        client = artifacts.getArtifact(Constants.CLIENT_IPA);

        if (!checkArtifacts())
        {
            return;
        }

        Map responseEntity = null;

        try
        {
            this.processPom(pom);

            TestFlightUploader uploader = new TestFlightUploader();
            TestFlightUploader.UploadRequest request = new TestFlightUploader.UploadRequest();
            request.apiToken = pomHandler.getApiToken();
            request.teamToken = pomHandler.getTeamToken();
            request.buildNotes = comment;
            request.lists = pomHandler.getDistroLists();
            request.notifyTeam = true;
            request.replace = true;

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
            if (user != null)
            {
                String subject = "TestFlight upload results: '" + build.getFullName() + "', '" + build.getBuildNumber() + "'";
                this.notifyPinner(user, null, subject, this.processResponseEntity(responseEntity));
            }
        }
    }

    @Override
    public void buildUnpinned(final @NotNull SBuild build, final @Nullable User user, final @Nullable String comment)
    {
        if (user != null)
        {
            StringBuilder builder = new StringBuilder("Notifying you (and everyone else) that you unpinned a build");
            builder.append("\nUser Comment:\n");
            builder.append(comment);

            this.notifyPinner(user, Constants.ADMIN_EMAIL, "You unpinned build: '" + build.getFullName() + "', '" + build.getBuildNumber() + "'", builder.toString());
        }
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

    private void notifyPinner(@NotNull User user, @Nullable String cc, @NotNull String subject, @NotNull String content)
    {
        Properties props = System.getProperties();
        props.setProperty(Constants.SMTP_SERVER_KEY, Constants.SMTP_SERVER_VALUE);

        Session session = Session.getDefaultInstance(props);

        try
        {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(Constants.FROM_ADDRESS));
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

        parser.parse(inputStream, pomHandler);
    }

    private File extractFile(BuildArtifact artifact) throws IOException
    {
        File file = File.createTempFile(Constants.CLIENT, "." + Constants.EXTENSION);

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

    private boolean checkArtifacts()
    {
        boolean pass = true;

        if (client == null)
        {
            Loggers.SERVER.info("Nothing to upload (i.e. client.ipa doesn't exist)");
            pass = false;
        }

        if (pom == null)
        {
            Loggers.SERVER.info("No pom.xml in project");
            pass = false;
        }

        return pass;
    }
}
