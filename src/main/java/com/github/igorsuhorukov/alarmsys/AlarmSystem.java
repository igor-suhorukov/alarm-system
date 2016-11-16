package com.github.igorsuhorukov.alarmsys;

//dependency:mvn:/com.github.igor-suhorukov:mvn-classloader:1.8
//dependency:mvn:/org.apache.camel:camel-core:2.18.0
//dependency:mvn:/org.apache.camel:camel-mail:2.18.0
//dependency:mvn:/io.rhiot:camel-webcam:0.1.4
//dependency:mvn:/io.rhiot:camel-pi4j:0.1.4
//dependency:mvn:/org.slf4j:slf4j-simple:1.6.6
import com.github.igorsuhorukov.smreed.dropship.MavenClassLoader;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultAttachment;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.management.event.CamelContextStartedEvent;
import org.apache.camel.management.event.CamelContextStoppedEvent;
import org.apache.camel.support.EventNotifierSupport;

import javax.mail.util.ByteArrayDataSource;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.EventObject;

class AlarmSystem {
    public static void main(String[] args) throws Exception{

        String login = System.getProperty("login");
        String password = System.getProperty("password");

        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.setName("Alarm system");
        Endpoint mailEndpoint = camelContext.getEndpoint(String.format("smtps://smtp.mail.ru:465?username=%s&password=%s&contentType=text/html&debugMode=true", login, password));
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("pi4j-gpio://3?mode=DIGITAL_INPUT&pullResistance=PULL_DOWN").routeId("GPIO read")
                        .choice()
                        .when(header("CamelPi4jPinState").isEqualTo("LOW"))
                        .to("controlbus:route?routeId=RaspberryPI Alarm&action=resume")
                        .otherwise()
                        .to("controlbus:route?routeId=RaspberryPI Alarm&action=suspend");

                from("timer://capture_image?delay=200&period=5000")
                        .routeId("RaspberryPI Alarm")
                        .to("webcam:spycam?resolution=HD720")
                        .setHeader("to").constant(login)
                        .setHeader("from").constant(login)
                        .setHeader("subject").constant("alarm image")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange it) throws Exception {
                                DefaultAttachment attachment = new DefaultAttachment(new ByteArrayDataSource(it.getIn().getBody(byte[].class), "image/jpeg"));
                                it.getIn().setBody(String.format("<html><head></head><body><img src=\"cid:alarm-image.jpeg\" /> %s</body></html>", new Date()));
                                attachment.addHeader("Content-ID", "<alarm-image.jpeg>");
                                it.getIn().addAttachmentObject("alarm-image.jpeg", attachment);
                                //set CL to avoid javax.activation.UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed
                                Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );

                            }
                        }).to(mailEndpoint);
            }
        });

        registerLifecycleActions(camelContext, mailEndpoint, login);
        camelContext.start();

        Class<?> hawtIoConsole = MavenClassLoader.usingCentralRepo()
                .forMavenCoordinates("io.hawt:hawtio-app:2.0.0").loadClass("io.hawt.app.App");
        Thread.currentThread().setContextClassLoader(hawtIoConsole.getClassLoader());
        Method main = hawtIoConsole.getMethod("main", String[].class);
        main.setAccessible(true);
        main.invoke(null, (Object) new String[]{"--port","10090"});
    }

    private static void registerLifecycleActions(final DefaultCamelContext camelContext, final Endpoint mailEndpoint, final String login) {
        camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {

            public boolean isEnabled(EventObject event) {
                return event instanceof CamelContextStartedEvent | event instanceof CamelContextStoppedEvent;
            }

            public void notify(EventObject event) throws Exception {
                String status = event instanceof CamelContextStartedEvent ? "up" : "down";
                if ("up".equals(status)){
                    Endpoint suspendEndpoint = camelContext.getEndpoint("controlbus:route?routeId=RaspberryPI Alarm&action=suspend");
                    suspendEndpoint.createProducer().process(suspendEndpoint.createExchange());
                }
                Exchange message = mailEndpoint.createExchange();
                message.getIn().setHeader("to", login);
                message.getIn().setHeader("from", login);
                message.getIn().setHeader("subject", "Alarm system is "+status);
                message.getIn().setBody("System is "+status+": "+new Date());
                mailEndpoint.createProducer().process(message);
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                try {
                    camelContext.stop();
                } catch (Exception e) {
                    System.exit(-1);
                }
            }
        });
    }
}
