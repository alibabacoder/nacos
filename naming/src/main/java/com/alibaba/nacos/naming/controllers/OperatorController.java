/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.nacos.naming.controllers;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.common.util.SystemUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.cluster.ServerListManager;
import com.alibaba.nacos.naming.cluster.ServerStatusManager;
import com.alibaba.nacos.naming.core.DistroMapper;
import com.alibaba.nacos.naming.core.Service;
import com.alibaba.nacos.naming.core.ServiceManager;
import com.alibaba.nacos.naming.misc.SwitchDomain;
import com.alibaba.nacos.naming.misc.SwitchEntry;
import com.alibaba.nacos.naming.misc.SwitchManager;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.push.PushService;
import com.alibaba.nacos.naming.web.NeedAuth;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * Operation for operators
 *
 * @author nkorange
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT + "/operator")
public class OperatorController {

    @Autowired
    private PushService pushService;

    @Autowired
    private SwitchManager switchManager;

    @Autowired
    private ServiceManager serviceManager;

    @Autowired
    private ServerListManager serverListManager;

    @Autowired
    private ServerStatusManager serverStatusManager;

    @Autowired
    private SwitchDomain switchDomain;

    @Autowired
    private DistroMapper distroMapper;

    @RequestMapping("/pushState")
    public JSONObject pushState(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        boolean detail = Boolean.parseBoolean(WebUtils.optional(request, "detail", "false"));
        boolean reset = Boolean.parseBoolean(WebUtils.optional(request, "reset", "false"));

        List<PushService.Receiver.AckEntry> failedPushes = PushService.getFailedPushes();
        int failedPushCount = pushService.getFailedPushCount();
        result.put("succeed", pushService.getTotalPush() - failedPushCount);
        result.put("total", pushService.getTotalPush());

        if (pushService.getTotalPush() > 0) {
            result.put("ratio", ((float) pushService.getTotalPush() - failedPushCount) / pushService.getTotalPush());
        } else {
            result.put("ratio", 0);
        }

        JSONArray dataArray = new JSONArray();
        if (detail) {
            for (PushService.Receiver.AckEntry entry : failedPushes) {
                try {
                    dataArray.add(new String(entry.origin.getData(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    dataArray.add("[encoding failure]");
                }
            }
            result.put("data", dataArray);
        }

        if (reset) {
            PushService.resetPushState();
        }

        result.put("reset", reset);

        return result;
    }

    @RequestMapping("/switches")
    public SwitchDomain switches(HttpServletRequest request) {
        return switchDomain;
    }

    @NeedAuth
    @RequestMapping("/updateSwitch")
    public String updateSwitch(HttpServletRequest request) throws Exception {
        Boolean debug = Boolean.parseBoolean(WebUtils.optional(request, "debug", "false"));
        String entry = WebUtils.required(request, "entry");
        String value = WebUtils.required(request, "value");

        switchManager.update(entry, value, debug);

        return "ok";
    }


    @RequestMapping(value = "/metrics", method = RequestMethod.GET)
    public JSONObject metrics(HttpServletRequest request) {

        JSONObject result = new JSONObject();

        int serviceCount = serviceManager.getServiceCount();
        int ipCount = serviceManager.getInstanceCount();

        int responsibleDomCount = serviceManager.getResponsibleServiceCount();
        int responsibleIPCount = serviceManager.getResponsibleInstanceCount();

        result.put("status", serverStatusManager.getServerStatus().name());
        result.put("serviceCount", serviceCount);
        result.put("instanceCount", ipCount);
        result.put("responsibleServiceCount", responsibleDomCount);
        result.put("responsibleInstanceCount", responsibleIPCount);
        result.put("cpu", SystemUtils.getCPU());
        result.put("load", SystemUtils.getLoad());
        result.put("mem", SystemUtils.getMem());

        return result;
    }

    @RequestMapping("/getResponsibleServer4Dom")
    public JSONObject getResponsibleServer4Dom(HttpServletRequest request) {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String dom = WebUtils.required(request, "dom");
        Service service = serviceManager.getService(namespaceId, dom);

        if (service == null) {
            throw new IllegalArgumentException("service not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsibleServer", distroMapper.mapSrv(dom));

        return result;
    }

    @RequestMapping("/getHealthyServerList")
    public JSONObject getHealthyServerList(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        result.put("healthyList", distroMapper.getHealthyList());

        return result;
    }

    @RequestMapping("/responsible")
    public JSONObject responsible(HttpServletRequest request) {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID,
            UtilsAndCommons.DEFAULT_NAMESPACE_ID);
        String dom = WebUtils.required(request, "dom");
        Service service = serviceManager.getService(namespaceId, dom);

        if (service == null) {
            throw new IllegalArgumentException("service not found");
        }

        JSONObject result = new JSONObject();

        result.put("responsible", distroMapper.responsible(dom));

        return result;
    }

    @RequestMapping("/distroStatus")
    public JSONObject distroStatus(HttpServletRequest request) {

        JSONObject result = new JSONObject();
        String action = WebUtils.optional(request, "action", "view");

        if (StringUtils.equals(SwitchEntry.ACTION_VIEW, action)) {
            result.put("status", serverListManager.getDistroConfig());
            return result;
        }

        if (StringUtils.equals(SwitchEntry.ACTION_CLEAN, action)) {
            serverListManager.clean();
            return result;
        }

        return result;
    }

    @RequestMapping("/serverStatus")
    public String serverStatus(HttpServletRequest request) {
        String serverStatus = WebUtils.required(request, "serverStatus");
        serverListManager.onReceiveServerStatus(serverStatus);
        return "ok";
    }
}
