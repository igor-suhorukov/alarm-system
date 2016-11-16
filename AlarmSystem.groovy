@Grab('org.apache.camel:camel-groovy:2.18.0')
@Grab('org.apache.camel:camel-core:2.18.0')
@Grab('org.apache.camel:camel-mail:2.18.0')
@Grab('io.rhiot:camel-webcam:0.1.4')
@Grab('io.rhiot:camel-pi4j:0.1.4')
@Grab('org.slf4j:slf4j-simple:1.6.6')
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.impl.DefaultAttachment
import org.apache.camel.impl.DefaultCamelContext
import org.apache.camel.management.event.CamelContextStartedEvent
import org.apache.camel.management.event.CamelContextStoppedEvent
import org.apache.camel.support.EventNotifierSupport

import javax.mail.util.ByteArrayDataSource
import com.github.igorsuhorukov.smreed.dropship.MavenClassLoader

def login = System.properties['login']
def password = System.properties['password']

def camelContext = new DefaultCamelContext()
camelContext.setName('Alarm system')
def mailEndpoint = camelContext.getEndpoint("smtps://smtp.mail.ru:465?username=${login}&password=${password}&contentType=text/html&debugMode=true")
camelContext.addRoutes(new RouteBuilder() {
    def void configure() {
        from('pi4j-gpio://3?mode=DIGITAL_INPUT&pullResistance=PULL_DOWN').routeId('GPIO read')
                .choice()
                .when(header('CamelPi4jPinState').isEqualTo("LOW"))
                    .to("controlbus:route?routeId=RaspberryPI Alarm&action=resume")
                .otherwise()
                    .to("controlbus:route?routeId=RaspberryPI Alarm&action=suspend");

        from("timer://capture_image?delay=200&period=5000")
                .routeId('RaspberryPI Alarm')
                .to("webcam:spycam?resolution=HD720")
                .setHeader('to').constant(login)
                .setHeader('from').constant(login)
                .setHeader('subject').constant('alarm image')
        .process{
            def attachment = new DefaultAttachment(new ByteArrayDataSource(it.in.body, 'image/jpeg'));
            it.in.setBody("<html><head></head><body><img src=\"cid:alarm-image.jpeg\" /> ${new Date()}</body></html>");
            attachment.addHeader("Content-ID", '<alarm-image.jpeg>');
            it.in.addAttachmentObject("alarm-image.jpeg", attachment);
            //set CL to avoid javax.activation.UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed
            Thread.currentThread().setContextClassLoader( getClass().getClassLoader() );
        }
        .to(mailEndpoint)
    }
})
registerLifecycleActions(camelContext, mailEndpoint, login)
camelContext.start()

def hawtIoConsole = MavenClassLoader.usingCentralRepo()
        .forMavenCoordinates('io.hawt:hawtio-app:2.0.0').loadClass('io.hawt.app.App')
Thread.currentThread().setContextClassLoader(hawtIoConsole.getClassLoader())
hawtIoConsole.main('--port','10090')


void registerLifecycleActions(camelContext, mailEndpoint, login) {

    camelContext.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
        boolean isEnabled(EventObject event) {
            return event instanceof CamelContextStartedEvent | event instanceof CamelContextStoppedEvent
        }

        void notify(EventObject event) throws Exception {
            def status = event instanceof CamelContextStartedEvent ? 'up' : 'down'
            if ('up' == status){
                def suspendEndpoint = camelContext.getEndpoint("controlbus:route?routeId=RaspberryPI Alarm&action=suspend")
                suspendEndpoint.createProducer().process(suspendEndpoint.createExchange())
            }
            def message = mailEndpoint.createExchange();
            message.in.setHeader('to', login)
            message.in.setHeader('from', login)
            message.in.setHeader('subject', "Alarm system is ${status}")
            message.in.setBody("System is ${status}: ${new Date()}");
            println "Alarm system is ${status}"
            mailEndpoint.createProducer().process(message)
        }
    })
    addShutdownHook { camelContext.stop() }
}