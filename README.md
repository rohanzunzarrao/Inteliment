# Inteliment
The "demo" folder contains the Eclipse project which includes the source code.
The "executable jar" folder contains the Spring boot jar that can be executed as "java -jar demo-0.0.1-SNAPSHOT.jar".

The jar is build using Spring Boot 1.5.1 and Java 8. The embedded Tomcat runs on the default port 8080, hence the command to access the services would be as listed below.

curl http://localhost:8080/counter-api/search -H "Authorization: Basic b3B0dXM6Y2FuZGlkYXRlcw==" -d "{\"searchText\":[\"Duis\", \"Sed\", \"Donec\", \"Augue\", \"Pellentesque\", \"123\"]}" -H "Content-Type: application/json" –X POST

curl http://localhost:8080/counter-api/top/5 -H "Authorization: Basic b3B0dXM6Y2FuZGlkYXRlcw==" -H "Accept: text/csv"

As there is only one authorization header that we know, the username/password "optus/candidates" has been specified in the application.properties file as a easier solution rather than implementation of the inMemory Authentication for Spring Security.
