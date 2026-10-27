package com.nihon.config;

import org.apache.catalina.Wrapper;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Tùy biến bảo mật cho máy chủ nhúng Apache Tomcat:
 * 1. Ẩn thông tin phiên bản máy chủ (Server header trống).
 * 2. Tắt hoàn toàn tính năng liệt kê danh mục thư mục (Directory listing / listings=false).
 */
@Component
public class TomcatSecurityCustomizer implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        if (factory == null) {
            return;
        }

        // 1. Ẩn định danh máy chủ (loại bỏ thông tin Apache Tomcat / phiên bản ra khỏi header)
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("server", "");
        });

        // 2. Tắt tính năng duyệt cây thư mục (Directory Listings) của DefaultServlet
        factory.addContextCustomizers(context -> {
            Wrapper defaultServlet = (Wrapper) context.findChild("default");
            if (defaultServlet != null) {
                defaultServlet.addInitParameter("listings", "false");
            }
        });
    }
}
