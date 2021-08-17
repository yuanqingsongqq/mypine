package com.mypine.gateway.config;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * @author ：yuanqingsong
 * @date ：Created in 2021/8/11 10:23
 * @description： 动态路由配置
 * @modified By：
 * @version:
 */
@Service
@Slf4j
public class DynamicRouteConfig implements ApplicationEventPublisherAware {

    @Autowired
    private RouteDefinitionWriter routedefinitionWriter;

    private ApplicationEventPublisher publisher;

    private String dataId = "gateway-router.properties";
    private String group = "DEFAULT_GROUP";
    @Value("${spring.cloud.nacos.server-addr}")
    private String serverAddr;
    @Value("${spring.cloud.nacos.config.namespace}")
    private String namespace;

    private static final List<String> ROUTE_LIST = new ArrayList<>();

    @PostConstruct
    public void dynamicRouteByNacosListener() {
        try {
            Properties prop = new Properties();
            prop.put("serverAddr", serverAddr);
            prop.put("namespace", namespace);
            ConfigService config = NacosFactory.createConfigService(prop);
            String content = config.getConfig(dataId, group, 5000);
            publisher(content);
            config.addListener(dataId, group, new Listener() {
                @Override
                public void receiveConfigInfo(String config) {
                    publisher(config);
                }
                @Override
                public Executor getExecutor() {
                    return null;
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 增加路由
     * @param def
     * @return
     */
    public Boolean addRoute(RouteDefinition def) {
        try {
            routedefinitionWriter.save(Mono.just(def)).subscribe();
            ROUTE_LIST.add(def.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }
    /**
     * 删除路由
     * @return
     */
    public Boolean clearRoute() {
        for(String id: ROUTE_LIST) {
            routedefinitionWriter.delete(Mono.just(id)).subscribe();
        }
        ROUTE_LIST.clear();
        return false;
    }
    /**
     * 发布路由
     */
    private void publisher(String config) {
        clearRoute();
        try {
            log.info("重新更新动态路由");
            List<RouteDefinition> gateway = JSONObject.parseArray(config, RouteDefinition.class);
            for(RouteDefinition route: gateway) {
                addRoute(route);
            }
            publisher.publishEvent(new RefreshRoutesEvent(this.routedefinitionWriter));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher app) {
        publisher = app;
    }
}
