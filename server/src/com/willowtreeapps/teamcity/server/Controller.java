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

import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.ModelAndView;
import com.willowtreeapps.teamcity.common.Util;

/**
 * Example custom page controller
 */
public class Controller extends BaseController {
  private PluginDescriptor myPluginDescriptor;

  public Controller(PluginDescriptor pluginDescriptor, WebControllerManager manager){
    myPluginDescriptor = pluginDescriptor;
    // this will make the controller accessible via <teamcity_url>\testflight-teamcity-server.html
    manager.registerController("/testflight-teamcity-server.html", this);
  }

  @Override
  protected ModelAndView doHandle(final @NotNull HttpServletRequest request, final @NotNull HttpServletResponse response) throws Exception {
    ModelAndView view = new ModelAndView(myPluginDescriptor.getPluginResourcesPath("testflight-teamcity-server.jsp"));
    final Map<String, Object> model = view.getModel();
    model.put("name", Util.NAME);
    return view;
  }
}
