package templatePrj.server;

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
import templatePrj.common.Util;
import templatePrj.server.testflight.TestFlightUploader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;

/**
 * Example server events listener
 */
public class ServerListener extends BuildServerAdapter {

  private static final String POM = "pom.xml";

    private static final String API_TOKEN = "testflight.api.token";

    private static final String TEAM_TOKEN = "testflight.team.token";

    private static final String DISTRIBUTION = "testflight.distribution";

    private static final String CLIENT = "client.ipa";

    private String apiToken;

    private String teamToken;

    private String distoLists;

  private SBuildServer myServer;

  public ServerListener(@NotNull final EventDispatcher<BuildServerListener> dispatcher, SBuildServer server) {
    dispatcher.addListener(this);
    myServer = server;
  }

  @Override
  public void serverStartup() {
    Loggers.SERVER.info("Plugin '" + Util.NAME + "'. Is running on server version " + myServer.getFullServerVersion() + ".");
  }

  @Override
  public void buildPinned(final @NotNull SBuild build, final @Nullable User user, final @Nullable String comment) {
      BuildArtifacts artifacts = build.getArtifacts(BuildArtifactsViewMode.VIEW_ALL);

      BuildArtifact pom = artifacts.getArtifact(POM);
      BuildArtifact client = artifacts.getArtifact(CLIENT);

      try
      {
          this.processPom(pom);

          TestFlightUploader uploader = new TestFlightUploader();
          TestFlightUploader.UploadRequest request = new TestFlightUploader.UploadRequest();
          request.apiToken = this.apiToken;
          request.teamToken = this.teamToken;
          request.buildNotes = comment;
          request.lists = distoLists;
          request.notifyTeam = true;
          request.replace = true;

          request.file = this.extractFile(client);

          uploader.upload(request);

          if (request.file.delete())
          {
              Loggers.SERVER.info("Tmp file deleted");
          }
      }
      catch (Exception e)
      {
          Loggers.SERVER.error("Error processing POM", e);
      }

  }

  private void processPom(BuildArtifact pom) throws ParserConfigurationException, SAXException, IOException
  {
      InputStream inputStream = pom.getInputStream();
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser parser = factory.newSAXParser();

      parser.parse(inputStream, new DefaultHandler()
      {
          boolean isApiToken = false;
          boolean isTeamToken = false;
          boolean isDistro = false;

          public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
          {
              if (qName.equals(API_TOKEN))
              {
                  isApiToken = true;
              }
              else if (qName.equals(TEAM_TOKEN))
              {
                  isTeamToken = true;
              }
              else if (qName.equals(DISTRIBUTION))
              {
                  isDistro = true;
              }
          }

          public void characters(char ch[], int start, int length) throws SAXException
          {
              if (isApiToken)
              {
                  apiToken = new String(ch).substring(start, start + length);
                  isApiToken = false;
              }
              else if (isTeamToken)
              {
                  teamToken = new String(ch).substring(start, start + length);
                  isTeamToken = false;
              }
              else if (isDistro)
              {
                  distoLists = new String(ch).substring(start, start + length);
                  isDistro = false;
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

        while((length = in.read(buffer)) > 0)
        {
            out.write(buffer, 0, length);
        }

        in.close();
        out.close();

        return file;
    }
}
