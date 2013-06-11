CamelOne Stateful Processes Demo
================================

To run this demo using maven just run

$ mvn clean install
$ mvn camel:run

For more help see the Apache Camel documentation

  http://camel.apache.org/
    
Running in a Karaf powered OSGi container:

> features:install camel
> features:install camel-jaxb
> osgi:install mvn:com.camelone.demo/excalibur/1.0.0
