package templatePrj.server;

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.springframework.web.servlet.ModelAndView;
import templatePrj.common.Util;

/**
 * Example custom page controller
 */
public class Controller extends BaseController {
  private PluginDescriptor myPluginDescriptor;

  public Controller(PluginDescriptor pluginDescriptor, WebControllerManager manager){
    myPluginDescriptor = pluginDescriptor;
    // this will make the controller accessible via <teamcity_url>\templatePrj.html
    manager.registerController("/templatePrj.html", this);
  }

  @Override
  protected ModelAndView doHandle(final HttpServletRequest request, final HttpServletResponse response) throws Exception {
    ModelAndView view = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("templatePrj.jsp"));
    final Map model = view.getModel();
    model.put("name", Util.NAME);
    return view;
  }
}
