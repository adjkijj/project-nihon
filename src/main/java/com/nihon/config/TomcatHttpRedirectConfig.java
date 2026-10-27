package com.nihon.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình tự động chuyển hướng HTTP sang HTTPS cho máy chủ nhúng Tomcat khi chạy chế độ SSL độc lập (Standalone).
 * Tự động tạo thêm cổng kết nối HTTP và bắt buộc chuyển đổi sang cổng HTTPS tương ứng.
 */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class TomcatHttpRedirectConfig {

    @Value("${server.port:8443}")
    private int httpsPort;

    @Value("${server.http.port:8080}")
    private int httpPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatHttpConnectorCustomizer() {
        return factory -> {
            Connector httpConnector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            httpConnector.setScheme("http");
            httpConnector.setPort(httpPort);
            httpConnector.setSecure(false);
            httpConnector.setRedirectPort(httpsPort);

            factory.addAdditionalTomcatConnectors(httpConnector);
            factory.addContextCustomizers((Context context) -> {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");

                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);

                context.addConstraint(securityConstraint);
            });
        };
    }
}
