# alarm-system
Connect webcam to USB port and reed switch between [GPIO №3](http://pi4j.com/pins/model-3b-rev1.html) and 3.3v pin on RaspberryPI 2/3

Run following command on RPI:

java -Dlogin=...YOUR_EMAIL...@mail.ru @Dpassword=******* -jar [groovy-grape-aether-2.4.5.4.jar](https://repo1.maven.org/maven2/com/github/igor-suhorukov/groovy-grape-aether/2.4.5.4/groovy-grape-aether-2.4.5.4.jar) AlarmSystem.groovy

Raspberry Pi 3 with connected reed switch: 
![Raspberry Pi 3 with connected reed switch](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/rpi_alarm_photo.jpg)


HawtIo web interface:
![alarm routes](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/camel-routes.png)
![GPIO route](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/camel-gpio.png)
![Webcam to email route](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/camel-webcam-route.png)

JVM metrics and threads:
![JVM metrics on RPI](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/camel-rpi.png)
![JVM threads on RPI](https://raw.githubusercontent.com/igor-suhorukov/alarm-system/master/img/camel-threads.png)
